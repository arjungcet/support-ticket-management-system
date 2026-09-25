package com.supportdesk.shared.web;

import com.supportdesk.shared.error.PayloadTooLargeException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Limits request bodies to {@link #MAX_BODY_BYTES} (spec/api-contract.md §1.1, security review M-4). The body stream
 * throws {@link PayloadTooLargeException} as soon as the limit is passed — or on the first read when
 * {@code Content-Length} already exceeds it — so an oversized body is never held in memory. The exception surfaces
 * while the controller argument is read and becomes {@code 413} Problem Details via the global handler; because
 * {@code Content-Type} is checked earlier, {@code 415} keeps precedence (§1.3).
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class RequestSizeLimitFilter extends OncePerRequestFilter {

    /** 128 KB: twice the largest valid request (every field at its maximum, fully JSON-escaped). */
    public static final int MAX_BODY_BYTES = 128 * 1024;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        chain.doFilter(new LimitedRequest(request), response);
    }

    private static final class LimitedRequest extends HttpServletRequestWrapper {

        private ServletInputStream limited;

        LimitedRequest(HttpServletRequest request) {
            super(request);
        }

        @Override
        public ServletInputStream getInputStream() throws IOException {
            if (limited == null) {
                limited = new LimitedInputStream(super.getInputStream(), getContentLengthLong() > MAX_BODY_BYTES);
            }
            return limited;
        }
    }

    private static final class LimitedInputStream extends ServletInputStream {

        private final ServletInputStream delegate;
        private final boolean declaredTooLarge;
        private long bytesRead;

        LimitedInputStream(ServletInputStream delegate, boolean declaredTooLarge) {
            this.delegate = delegate;
            this.declaredTooLarge = declaredTooLarge;
        }

        @Override
        public int read() throws IOException {
            checkBeforeRead();
            int b = delegate.read();
            if (b != -1) {
                count(1);
            }
            return b;
        }

        @Override
        public int read(byte[] buffer, int offset, int length) throws IOException {
            checkBeforeRead();
            int n = delegate.read(buffer, offset, length);
            if (n > 0) {
                count(n);
            }
            return n;
        }

        private void checkBeforeRead() {
            if (declaredTooLarge) {
                throw new PayloadTooLargeException(MAX_BODY_BYTES);
            }
        }

        private void count(int n) {
            bytesRead += n;
            if (bytesRead > MAX_BODY_BYTES) {
                throw new PayloadTooLargeException(MAX_BODY_BYTES);
            }
        }

        @Override
        public boolean isFinished() {
            return delegate.isFinished();
        }

        @Override
        public boolean isReady() {
            return delegate.isReady();
        }

        @Override
        public void setReadListener(ReadListener listener) {
            delegate.setReadListener(listener);
        }
    }
}
