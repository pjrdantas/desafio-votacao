package br.com.sicredi.desafiovotacao.adapter.in.web.security;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.*;
import tools.jackson.databind.json.JsonMapper;
import java.time.Clock;
import java.util.concurrent.atomic.AtomicInteger;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
class AuthRateLimitFilterTest {
    @Test void limitaPorOrigemSemAceitarIpForjadoERetomaAposUmMinuto() throws Exception {
        Clock clock=mock(Clock.class);
        when(clock.millis()).thenReturn(0L);
        when(clock.instant()).thenReturn(java.time.Instant.EPOCH);
        var filter=new AuthRateLimitFilter(clock,2,new SecurityErrorWriter(JsonMapper.builder().build(),clock));
        var contador=new AtomicInteger();
        for(int i=0;i<3;i++) {
            var request=new MockHttpServletRequest("POST","/api/v1/auth/login");
            request.setRemoteAddr("127.0.0.1");
            request.addHeader("X-Forwarded-For","192.0.2."+i);
            var response=new MockHttpServletResponse();
            filter.doFilter(request,response,(req,res)->contador.incrementAndGet());
            if(i==2) {
                assertThat(response.getStatus()).isEqualTo(429);
                assertThat(response.getHeader("Retry-After")).isEqualTo("60");
                assertThat(response.getContentAsString()).contains("LIMITE_TENTATIVAS");
            }
        }
        assertThat(contador.get()).isEqualTo(2);
        when(clock.millis()).thenReturn(60000L);
        var request=new MockHttpServletRequest("POST","/api/v1/auth/login"); request.setRemoteAddr("127.0.0.1");
        filter.doFilter(request,new MockHttpServletResponse(),(req,res)->contador.incrementAndGet());
        assertThat(contador.get()).isEqualTo(3);
    }
}
