package com.pro.ereportbacken.security;

import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

public class CachedBodyHttpServletRequest extends HttpServletRequestWrapper {

    private final byte[] body;

    private CachedBodyHttpServletRequest(HttpServletRequest request, byte[] body) {
        super(request);
        this.body = body;
    }

    public static CachedBodyHttpServletRequest capture(HttpServletRequest request, int maximumBytes) throws IOException {
        long declaredLength = request.getContentLengthLong();
        if (declaredLength > maximumBytes) {
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "单次签名请求体不能超过8MiB，请使用分片接口。");
        }
        byte[] body = request.getInputStream().readNBytes(maximumBytes + 1);
        if (body.length > maximumBytes) {
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "单次签名请求体不能超过8MiB，请使用分片接口。");
        }
        return new CachedBodyHttpServletRequest(request, body);
    }

    public byte[] body() {
        return body.clone();
    }

    @Override
    public ServletInputStream getInputStream() {
        ByteArrayInputStream input = new ByteArrayInputStream(body);
        return new ServletInputStream() {
            @Override
            public boolean isFinished() {
                return input.available() == 0;
            }

            @Override
            public boolean isReady() {
                return true;
            }

            @Override
            public void setReadListener(ReadListener readListener) {
                if (readListener == null) {
                    throw new IllegalArgumentException("ReadListener must not be null.");
                }
                try {
                    if (isFinished()) {
                        readListener.onAllDataRead();
                    } else {
                        readListener.onDataAvailable();
                    }
                } catch (IOException exception) {
                    readListener.onError(exception);
                }
            }

            @Override
            public int read() {
                return input.read();
            }

            @Override
            public int read(byte[] bytes, int offset, int length) {
                return input.read(bytes, offset, length);
            }
        };
    }

    @Override
    public BufferedReader getReader() {
        Charset charset = getCharacterEncoding() == null
                ? StandardCharsets.UTF_8
                : Charset.forName(getCharacterEncoding());
        return new BufferedReader(new InputStreamReader(getInputStream(), charset));
    }

    @Override
    public int getContentLength() {
        return body.length;
    }

    @Override
    public long getContentLengthLong() {
        return body.length;
    }
}
