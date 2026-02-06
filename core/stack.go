package core

import (
	"os"
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
)

type TunStack struct {
	tunFile *os.File
	s       *stack.Stack
	ep      *channel.Endpoint
}

func NewTunStack(fd int, cb EngineCallbacks) (*TunStack, error) {
	s := stack.New(stack.Options{
		NetworkProtocols:   []stack.NetworkProtocolFactory{ipv4.NewProtocol, ipv6.NewProtocol},
		TransportProtocols: []stack.TransportProtocolFactory{tcp.NewProtocol},
	})
	ep := channel.New(256, 1500, "")
	s.CreateNIC(1, ep)
	s.SetRouteTable([]tcpip.Route{{Destination: header.IPv4EmptySubnet, NIC: 1}, {Destination: header.IPv6EmptySubnet, NIC: 1}})
	
	f := tcp.NewForwarder(s, 0, 65535, func(r *tcp.ForwarderRequest) {
		var wq waiter.Queue
		tep, _ := r.CreateEndpoint(&wq)
		r.Complete(false)
		addr, _ := tep.GetLocalAddress()
		go handleProxyConnection(gonet.NewTCPConn(&wq, tep), addr.Addr.String() + ":443", cb)
	})
	s.SetTransportProtocolHandler(tcp.ProtocolNumber, f.HandlePacket)

	return &TunStack{tunFile: os.NewFile(uintptr(fd), "tun"), s: s, ep: ep}, nil
}

func (ts *TunStack) Start() {
	go func() {
		buf := make([]byte, 1500)
		for {
			n, err := ts.tunFile.Read(buf)
			if err != nil { break }
			v := buffer.NewViewWithData(buf[:n])
			ts.ep.InjectInbound(header.IPv4ProtocolNumber, stack.NewPacketBuffer(stack.PacketBufferOptions{Payload: buffer.MakeWithView(v)}))
		}
	}()
}