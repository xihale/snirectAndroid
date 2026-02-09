package core

import (
	"context"
	"fmt"
	"net"
	"os"
	"sync/atomic"

	"gvisor.dev/gvisor/pkg/buffer"
	"gvisor.dev/gvisor/pkg/tcpip"
	"gvisor.dev/gvisor/pkg/tcpip/adapters/gonet"
	"gvisor.dev/gvisor/pkg/tcpip/header"
	"gvisor.dev/gvisor/pkg/tcpip/link/channel"
	"gvisor.dev/gvisor/pkg/tcpip/network/ipv4"
	"gvisor.dev/gvisor/pkg/tcpip/network/ipv6"
	"gvisor.dev/gvisor/pkg/tcpip/stack"
	"gvisor.dev/gvisor/pkg/tcpip/transport/icmp"
	"gvisor.dev/gvisor/pkg/tcpip/transport/tcp"
	"gvisor.dev/gvisor/pkg/tcpip/transport/udp"
	"gvisor.dev/gvisor/pkg/waiter"
)

type TunStack struct {
	tunFile *os.File
	s       *stack.Stack
	ep      *channel.Endpoint
	done    chan struct{}
}

func NewTunStack(fd int, config *Config, cb EngineCallbacks) (*TunStack, error) {
	s := stack.New(stack.Options{
		NetworkProtocols:   []stack.NetworkProtocolFactory{ipv4.NewProtocol, ipv6.NewProtocol},
		TransportProtocols: []stack.TransportProtocolFactory{tcp.NewProtocol, udp.NewProtocol, icmp.NewProtocol4, icmp.NewProtocol6},
	})

	mtu := 1500
	if config != nil && config.MTU > 0 {
		mtu = config.MTU
	}

	ep := channel.New(1024, uint32(mtu), "")
	if err := s.CreateNIC(1, ep); err != nil {
		return nil, fmt.Errorf("create nic: %v", err)
	}

	if err := s.SetPromiscuousMode(1, true); err != nil {
		return nil, fmt.Errorf("set promiscuous mode: %v", err)
	}
	if err := s.SetSpoofing(1, true); err != nil {
		return nil, fmt.Errorf("set spoofing: %v", err)
	}

	s.AddProtocolAddress(1, tcpip.ProtocolAddress{
		AddressWithPrefix: tcpip.AddressWithPrefix{
			Address:   tcpip.AddrFrom4([4]byte{10, 0, 0, 2}),
			PrefixLen: 24,
		},
	}, stack.AddressProperties{})

	s.SetForwardingDefaultAndAllNICs(ipv4.ProtocolNumber, true)
	s.SetForwardingDefaultAndAllNICs(ipv6.ProtocolNumber, true)

	// Route everything to NIC 1
	s.SetRouteTable([]tcpip.Route{
		{Destination: header.IPv4EmptySubnet, NIC: 1},
		{Destination: header.IPv6EmptySubnet, NIC: 1},
	})

	// Use a large receive window
	f := tcp.NewForwarder(s, 0, 10000, func(r *tcp.ForwarderRequest) {
		defer func() {
			if r := recover(); r != nil {
				LogError("FATAL PANIC in TCP Forwarder: %v", r)
			}
		}()

		id := r.ID()
		dest := net.JoinHostPort(id.LocalAddress.String(), fmt.Sprintf("%d", id.LocalPort))
		LogDebug("TCP Forwarder: Request from %s to %s", id.RemoteAddress, dest)

		var wq waiter.Queue
		tep, err := r.CreateEndpoint(&wq)
		if err != nil {
			LogError("TCP Forwarder: CreateEndpoint failed: %v", err)
			r.Complete(true)
			return
		}

		if tep == nil {
			LogError("TCP Forwarder: Endpoint is nil")
			r.Complete(true)
			return
		}

		LogDebug("TCP Forwarder: Configuring socket...")
		tep.SocketOptions().SetKeepAlive(true)

		LogDebug("TCP Forwarder: Completing request...")
		r.Complete(false)

		LogDebug("TCP Forwarder: Creating gonet connection...")
		conn := gonet.NewTCPConn(&wq, tep)
		if conn == nil {
			LogError("TCP Forwarder: gonet connection is nil")
			return
		}

		LogDebug("TCP Forwarder: Starting proxy handler...")
		if id.LocalPort == 443 {
			go handleProxyConnection(conn, dest, nil)
		} else {
			go forwardDirect(conn, dest, nil)
		}
		LogDebug("TCP Forwarder: Handler started")
	})
	s.SetTransportProtocolHandler(tcp.ProtocolNumber, f.HandlePacket)

	uf := udp.NewForwarder(s, func(r *udp.ForwarderRequest) bool {
		defer func() {
			if r := recover(); r != nil {
				LogError("PANIC in UDP Forwarder: %v", r)
			}
		}()
		dest := fmt.Sprintf("%s:%d", r.ID().LocalAddress, r.ID().LocalPort)

		var wq waiter.Queue
		uep, err := r.CreateEndpoint(&wq)
		if err != nil {
			LogError("UDP Forwarder: CreateEndpoint failed for %s: %v", dest, err)
			return false
		}

		if r.ID().LocalPort == 53 {
			go handleDNSConnection(gonet.NewUDPConn(&wq, uep), nil)
			return true
		}

		go handleUDPForwardDirect(gonet.NewUDPConn(&wq, uep), dest)
		return true
	})
	s.SetTransportProtocolHandler(udp.ProtocolNumber, uf.HandlePacket)

	return &TunStack{
		tunFile: os.NewFile(uintptr(fd), "tun"),
		s:       s,
		ep:      ep,
		done:    make(chan struct{}),
	}, nil
}

func (ts *TunStack) Start() {
	LogInfo("TUN Stack: Starting read loop on FD: %d", ts.tunFile.Fd())

	go func() {
		defer func() {
			if r := recover(); r != nil {
				LogError("PANIC in TUN read loop: %v", r)
			}
		}()
		buf := make([]byte, 2048)
		for {
			if ts.tunFile == nil {
				LogError("TUN read loop: tunFile is nil!")
				return
			}
			n, err := ts.tunFile.Read(buf)
			if err != nil {
				LogError("TUN Read Error: %v", err)
				return
			}
			if n > 0 {
				atomic.AddInt64(&uploadBytes, int64(n))
				ver := header.IPVersion(buf)
				var proto tcpip.NetworkProtocolNumber
				if ver == 4 {
					proto = header.IPv4ProtocolNumber
				} else if ver == 6 {
					proto = header.IPv6ProtocolNumber
				} else {
					LogDebug("TUN read: unknown IP version %d", ver)
					continue
				}

				if ts.ep == nil {
					LogError("TUN read loop: ep is nil!")
					return
				}

				v := buffer.NewViewWithData(buf[:n])
				pkt := stack.NewPacketBuffer(stack.PacketBufferOptions{Payload: buffer.MakeWithView(v)})
				ts.ep.InjectInbound(proto, pkt)
				pkt.DecRef()
			}
		}
	}()

	go func() {
		defer func() {
			if r := recover(); r != nil {
				LogError("PANIC in TUN write loop: %v", r)
			}
		}()
		ctx := context.Background()
		for {
			pkt := ts.ep.ReadContext(ctx)
			if pkt == nil {
				continue
			}

			vv := pkt.ToView()
			data := vv.AsSlice()

			if len(data) > 0 {
				atomic.AddInt64(&downloadBytes, int64(len(data)))
				ts.tunFile.Write(data)
			}
			pkt.DecRef()
		}
	}()
}

func (ts *TunStack) Stop() {
	close(ts.done)
}
