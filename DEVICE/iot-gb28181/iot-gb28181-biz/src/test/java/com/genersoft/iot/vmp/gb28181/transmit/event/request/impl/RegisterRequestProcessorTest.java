package com.genersoft.iot.vmp.gb28181.transmit.event.request.impl;

import com.genersoft.iot.vmp.gb28181.transmit.SIPSender;
import gov.nist.javax.sip.message.SIPRequest;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import javax.sip.RequestEvent;
import javax.sip.SipFactory;
import javax.sip.message.Response;
import java.net.InetAddress;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RegisterRequestProcessorTest {

    @Test
    void register_without_expires_header_returns_bad_request() throws Exception {
        SIPRequest request = (SIPRequest) SipFactory.getInstance()
                .createMessageFactory()
                .createRequest(
                        "REGISTER sip:34020000002000000001@127.0.0.1 SIP/2.0\r\n"
                                + "Via: SIP/2.0/UDP 127.0.0.1:5060;branch=z9hG4bK-missing-expires\r\n"
                                + "From: <sip:44010200493432381460@127.0.0.1>;tag=1\r\n"
                                + "To: <sip:44010200493432381460@127.0.0.1>\r\n"
                                + "Call-ID: missing-expires@127.0.0.1\r\n"
                                + "CSeq: 1 REGISTER\r\n"
                                + "Contact: <sip:44010200493432381460@127.0.0.1:5060>\r\n"
                                + "Max-Forwards: 70\r\n"
                                + "Content-Length: 0\r\n\r\n"
                );
        request.setLocalAddress(InetAddress.getLoopbackAddress());
        RequestEvent event = mock(RequestEvent.class);
        when(event.getRequest()).thenReturn(request);

        RegisterRequestProcessor processor = new RegisterRequestProcessor();
        SIPSender sipSender = mock(SIPSender.class);
        ReflectionTestUtils.setField(processor, "sipSender", sipSender);

        processor.process(event);

        verify(sipSender).transmitRequest(
                eq(InetAddress.getLoopbackAddress().getHostAddress()),
                argThat(response -> {
                    assertEquals(Response.BAD_REQUEST, ((Response) response).getStatusCode());
                    return true;
                })
        );
    }
}
