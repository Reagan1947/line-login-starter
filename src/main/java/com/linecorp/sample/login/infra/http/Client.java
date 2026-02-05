/*
 * Copyright 2016 LY Corporation
 *
 * LY Corporation licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package com.linecorp.sample.login.infra.http;

import java.io.IOException;
import java.security.KeyStore;
import java.util.Arrays;
import java.util.Collections;
import java.util.function.Function;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;

import okhttp3.ConnectionSpec;
import okhttp3.OkHttpClient;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Call;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

/**
 * <p>HTTP request execution<p>
 */
public final class Client {

    /**
     * <p>execute HTTP requests</p>
     * <p>Uses TLS 1.2+ only to satisfy LINE API security requirements (avoid insufficient_security).</p>
     */
    public static <T, R> R getClient(
            final String url,
            final Class<T> service,
            final Function<T, Call<R>> function){

        HttpLoggingInterceptor interceptor = new HttpLoggingInterceptor();
        interceptor.setLevel(HttpLoggingInterceptor.Level.BODY);

        SSLSocketFactory sslSocketFactory = createTls12SocketFactory();
        X509TrustManager trustManager = getDefaultTrustManager();
        OkHttpClient client = new OkHttpClient.Builder()
                .sslSocketFactory(sslSocketFactory, trustManager)
                .connectionSpecs(Collections.singletonList(ConnectionSpec.MODERN_TLS))
                .addInterceptor(interceptor)
                .build();

        Retrofit retrofit = new Retrofit.Builder()
            .baseUrl(url)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build();
        T t = retrofit.create(service);
        Call<R> call = function.apply(t);
        try {
            return call.execute().body();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * SSLSocketFactory that only enables TLS 1.2 (and 1.3 if supported) to avoid
     * "insufficient_security" from servers that reject older protocols.
     */
    private static SSLSocketFactory createTls12SocketFactory() {
        try {
            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, null, null);
            final SSLSocketFactory delegate = sslContext.getSocketFactory();
            return new SSLSocketFactory() {
                @Override
                public String[] getDefaultCipherSuites() {
                    return delegate.getDefaultCipherSuites();
                }

                @Override
                public String[] getSupportedCipherSuites() {
                    return delegate.getSupportedCipherSuites();
                }

                @Override
                public java.net.Socket createSocket(java.net.Socket s, String host, int port, boolean autoClose)
                        throws IOException {
                    SSLSocket socket = (SSLSocket) delegate.createSocket(s, host, port, autoClose);
                    socket.setEnabledProtocols(onlyTls12AndAbove(socket.getSupportedProtocols()));
                    return socket;
                }

                @Override
                public java.net.Socket createSocket(String host, int port) throws IOException {
                    SSLSocket socket = (SSLSocket) delegate.createSocket(host, port);
                    socket.setEnabledProtocols(onlyTls12AndAbove(socket.getSupportedProtocols()));
                    return socket;
                }

                @Override
                public java.net.Socket createSocket(String host, int port, java.net.InetAddress localHost, int localPort)
                        throws IOException {
                    SSLSocket socket = (SSLSocket) delegate.createSocket(host, port, localHost, localPort);
                    socket.setEnabledProtocols(onlyTls12AndAbove(socket.getSupportedProtocols()));
                    return socket;
                }

                @Override
                public java.net.Socket createSocket(java.net.InetAddress host, int port) throws IOException {
                    SSLSocket socket = (SSLSocket) delegate.createSocket(host, port);
                    socket.setEnabledProtocols(onlyTls12AndAbove(socket.getSupportedProtocols()));
                    return socket;
                }

                @Override
                public java.net.Socket createSocket(java.net.InetAddress address, int port,
                        java.net.InetAddress localAddress, int localPort) throws IOException {
                    SSLSocket socket = (SSLSocket) delegate.createSocket(address, port, localAddress, localPort);
                    socket.setEnabledProtocols(onlyTls12AndAbove(socket.getSupportedProtocols()));
                    return socket;
                }
            };
        } catch (Exception e) {
            throw new RuntimeException("Failed to create TLS 1.2 SSLSocketFactory", e);
        }
    }

    private static String[] onlyTls12AndAbove(String[] protocols) {
        return Arrays.stream(protocols)
                .filter(p -> "TLSv1.2".equals(p) || "TLSv1.3".equals(p))
                .toArray(String[]::new);
    }

    private static X509TrustManager getDefaultTrustManager() {
        try {
            TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            tmf.init((KeyStore) null);
            for (TrustManager tm : tmf.getTrustManagers()) {
                if (tm instanceof X509TrustManager) {
                    return (X509TrustManager) tm;
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to get default TrustManager", e);
        }
        throw new RuntimeException("No X509TrustManager in default TrustManagerFactory");
    }
}
