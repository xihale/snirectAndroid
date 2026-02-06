package core

import (
	"gvisor.dev/gvisor/pkg/buffer"
	"gvisor.dev/gvisor/pkg/tcpip"
	"gvisor.dev/gvisor/pkg/tcpip/adapters/gonet"
	"gvisor.dev/gvisor/pkg/tcpip/header"
	"gvisor.dev/gvisor/pkg/tcpip/link/channel"
	"gvisor.dev/gvisor/pkg/tcpip/network/ipv4"
	"gvisor.dev/gvisor/pkg/tcpip/network/ipv6"
	"gvisor.dev/gvisor/pkg/tcpip/stack"
	"gvisor.dev/gvisor/pkg/tcpip/transport/tcp"
	"gvisor.dev/gvisor/pkg/waiter"
	"os"
)

type TunStack struct {
	tunFile *os.File
	s       *stack.Stack
	ep      *channel.Endpoint
}

func NewTunStack(fd int, config *Config, cb EngineCallbacks) (*TunStack, error) {
	s := stack.New(stack.Options{
		NetworkProtocols:   []stack.NetworkProtocolFactory{ipv4.NewProtocol, ipv6.NewProtocol},
		TransportProtocols: []stack.TransportProtocolFactory{tcp.NewProtocol},
	})

	mtu := 1500
	if config != nil && config.MTU > 0 {
		mtu = config.MTU
	}

	ep := channel.New(256, uint32(mtu), "")
	s.CreateNIC(1, ep)
	s.SetRouteTable([]tcpip.Route{{Destination: header.IPv4EmptySubnet, NIC: 1}, {Destination: header.IPv6EmptySubnet, NIC: 1}})

	f := tcp.NewForwarder(s, 0, 65535, func(r *tcp.ForwarderRequest) {
		var wq waiter.Queue
		tep, _ := r.CreateEndpoint(&wq)
		r.Complete(false)
		addr, _ := tep.GetLocalAddress()
		go handleProxyConnection(gonet.NewTCPConn(&wq, tep), addr.Addr.String()+":443", cb)
	})
	s.SetTransportProtocolHandler(tcp.ProtocolNumber, f.HandlePacket)

	return &TunStack{tunFile: os.NewFile(uintptr(fd), "tun"), s: s, ep: ep}, nil
}

func (ts *TunStack) Start() {
	go func() {
		buf := make([]byte, 1500)
		for {
			n, err := ts.tunFile.Read(buf)
			if err != nil {
				break
			}
			v := buffer.NewViewWithData(buf[:n])
			ts.ep.InjectInbound(header.IPv4ProtocolNumber, stack.NewPacketBuffer(stack.PacketBufferOptions{Payload: buffer.MakeWithView(v)}))
		}
	}()
}
