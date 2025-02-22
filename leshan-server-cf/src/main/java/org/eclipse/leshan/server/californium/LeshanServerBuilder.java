/*******************************************************************************
 * Copyright (c) 2013-2015 Sierra Wireless and others.
 * 
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * and Eclipse Distribution License v1.0 which accompany this distribution.
 * 
 * The Eclipse Public License is available at
 *    http://www.eclipse.org/legal/epl-v10.html
 * and the Eclipse Distribution License is available at
 *    http://www.eclipse.org/org/documents/edl-v10.html.
 * 
 * Contributors:
 *     Sierra Wireless - initial API and implementation
 *     Achim Kraus (Bosch Software Innovations GmbH) - use Lwm2mEndpointContextMatcher
 *                                                     for secure endpoint.
 *     Achim Kraus (Bosch Software Innovations GmbH) - use CoapEndpointBuilder
 *******************************************************************************/
package org.eclipse.leshan.server.californium;

import java.net.InetSocketAddress;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.Arrays;

import org.eclipse.californium.core.network.CoapEndpoint;
import org.eclipse.californium.core.network.config.NetworkConfig;
import org.eclipse.californium.core.network.config.NetworkConfig.Keys;
import org.eclipse.californium.elements.UDPConnector;
import org.eclipse.californium.scandium.DTLSConnector;
import org.eclipse.californium.scandium.config.DtlsConnectorConfig;
import org.eclipse.californium.scandium.dtls.CertificateType;
import org.eclipse.leshan.LwM2m;
import org.eclipse.leshan.core.californium.DefaultEndpointFactory;
import org.eclipse.leshan.core.californium.EndpointFactory;
import org.eclipse.leshan.core.node.LwM2mNode;
import org.eclipse.leshan.core.node.codec.DefaultLwM2mNodeDecoder;
import org.eclipse.leshan.core.node.codec.DefaultLwM2mNodeEncoder;
import org.eclipse.leshan.core.node.codec.LwM2mNodeDecoder;
import org.eclipse.leshan.core.node.codec.LwM2mNodeEncoder;
import org.eclipse.leshan.core.observation.Observation;
import org.eclipse.leshan.core.request.exception.ClientSleepingException;
import org.eclipse.leshan.server.californium.registration.CaliforniumRegistrationStore;
import org.eclipse.leshan.server.californium.registration.InMemoryRegistrationStore;
import org.eclipse.leshan.server.model.LwM2mModelProvider;
import org.eclipse.leshan.server.model.StandardModelProvider;
import org.eclipse.leshan.server.queue.ClientAwakeTimeProvider;
import org.eclipse.leshan.server.queue.StaticClientAwakeTimeProvider;
import org.eclipse.leshan.server.registration.RandomStringRegistrationIdProvider;
import org.eclipse.leshan.server.registration.Registration;
import org.eclipse.leshan.server.registration.RegistrationIdProvider;
import org.eclipse.leshan.server.registration.RegistrationStore;
import org.eclipse.leshan.server.security.Authorizer;
import org.eclipse.leshan.server.security.DefaultAuthorizer;
import org.eclipse.leshan.server.security.InMemorySecurityStore;
import org.eclipse.leshan.server.security.SecurityInfo;
import org.eclipse.leshan.server.security.SecurityStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Class helping you to build and configure a Californium based Leshan Lightweight M2M server. Usage: create it, call
 * the different setters for changing the configuration and then call the {@link #build()} method for creating the
 * {@link LeshanServer} ready to operate.
 */
public class LeshanServerBuilder {

    private static final Logger LOG = LoggerFactory.getLogger(LeshanServerBuilder.class);

    private CaliforniumRegistrationStore registrationStore;
    private SecurityStore securityStore;
    private LwM2mModelProvider modelProvider;
    private Authorizer authorizer;
    private ClientAwakeTimeProvider awakeTimeProvider;
    private RegistrationIdProvider registrationIdProvider;

    private InetSocketAddress localAddress;
    private InetSocketAddress localSecureAddress;

    private LwM2mNodeEncoder encoder;
    private LwM2mNodeDecoder decoder;

    private PublicKey publicKey;
    private PrivateKey privateKey;
    private X509Certificate[] certificateChain;
    private Certificate[] trustedCertificates;

    private NetworkConfig coapConfig;
    private DtlsConnectorConfig.Builder dtlsConfigBuilder;

    private EndpointFactory endpointFactory;

    private boolean noSecuredEndpoint;
    private boolean noUnsecuredEndpoint;
    private boolean noQueueMode = false;

    /**
     * <p>
     * Set the address/port for unsecured CoAP Server.
     * </p>
     * 
     * By default a wildcard address and the default CoAP port(5683) is used
     * 
     * @param hostname The address to bind. If null wildcard address is used.
     * @param port A valid port value is between 0 and 65535. A port number of zero will let the system pick up an
     *        ephemeral port in a bind operation.
     */
    public LeshanServerBuilder setLocalAddress(String hostname, int port) {
        if (hostname == null) {
            this.localAddress = new InetSocketAddress(port);
        } else {
            this.localAddress = new InetSocketAddress(hostname, port);
        }
        return this;
    }

    /**
     * <p>
     * Set the address for unsecured CoAP Server.
     * </p>
     * 
     * By default a wildcard address and the default CoAP port(5683) is used.
     */
    public LeshanServerBuilder setLocalAddress(InetSocketAddress localAddress) {
        this.localAddress = localAddress;
        return this;
    }

    /**
     * <p>
     * Set the address/port for secured CoAP Server (Using DTLS).
     * <p>
     * 
     * By default a wildcard address and the default CoAPs port(5684) is used.
     * 
     * @param hostname The address to bind. If null wildcard address is used.
     * @param port A valid port value is between 0 and 65535. A port number of zero will let the system pick up an
     *        ephemeral port in a bind operation.
     */
    public LeshanServerBuilder setLocalSecureAddress(String hostname, int port) {
        if (hostname == null) {
            this.localSecureAddress = new InetSocketAddress(port);
        } else {
            this.localSecureAddress = new InetSocketAddress(hostname, port);
        }
        return this;
    }

    /**
     * <p>
     * Set the address for secured CoAP Server (Using DTLS).
     * </p>
     * 
     * By default a wildcard address and the default CoAP port(5684) is used.
     */
    public LeshanServerBuilder setLocalSecureAddress(InetSocketAddress localSecureAddress) {
        this.localSecureAddress = localSecureAddress;
        return this;
    }

    /**
     * <p>
     * Set your {@link RegistrationStore} implementation which stores {@link Registration} and {@link Observation}.
     * </p>
     * By default the {@link InMemoryRegistrationStore} implementation is used.
     * 
     */
    public LeshanServerBuilder setRegistrationStore(CaliforniumRegistrationStore registrationStore) {
        this.registrationStore = registrationStore;
        return this;
    }

    /**
     * <p>
     * Set your {@link SecurityStore} implementation which stores {@link SecurityInfo}.
     * </p>
     * By default no security store is set. It is needed for secured connection if you are using the defaultAuthorizer
     * or if you want PSK feature activated. An {@link InMemorySecurityStore} is provided to start using secured
     * connection.
     * 
     */
    public LeshanServerBuilder setSecurityStore(SecurityStore securityStore) {
        this.securityStore = securityStore;
        return this;
    }

    /**
     * <p>
     * Set your {@link Authorizer} implementation to define if a device if authorize to register to this server.
     * </p>
     * By default the {@link DefaultAuthorizer} implementation is used, it needs a security store to accept secured
     * connection.
     */
    public LeshanServerBuilder setAuthorizer(Authorizer authorizer) {
        this.authorizer = authorizer;
        return this;
    }

    /**
     * <p>
     * Set your {@link LwM2mModelProvider} implementation.
     * </p>
     * By default the {@link StandardModelProvider} implementation is used which support all core objects for all
     * devices.
     * 
     */
    public LeshanServerBuilder setObjectModelProvider(LwM2mModelProvider objectModelProvider) {
        this.modelProvider = objectModelProvider;
        return this;
    }

    /**
     * <p>
     * Set the {@link PublicKey} of the server which will be used for RawPublicKey DTLS authentication.
     * </p>
     * This should be used for RPK support only. If you support RPK and X509,
     * {@link LeshanServerBuilder#setCertificateChain(X509Certificate[])} should be used.
     */
    public LeshanServerBuilder setPublicKey(PublicKey publicKey) {
        this.publicKey = publicKey;
        return this;
    }

    /**
     * Set the {@link PrivateKey} of the server which will be used for RawPublicKey(RPK) and X509 DTLS authentication.
     */
    public LeshanServerBuilder setPrivateKey(PrivateKey privateKey) {
        this.privateKey = privateKey;
        return this;
    }

    /**
     * <p>
     * Set the CertificateChain of the server which will be used for X509 DTLS authentication.
     * </p>
     * For RPK the public key will be extract from the first X509 certificate of the certificate chain. If you only need
     * RPK support, use {@link LeshanServerBuilder#setPublicKey(PublicKey)} instead.
     */
    public <T extends X509Certificate> LeshanServerBuilder setCertificateChain(T[] certificateChain) {
        this.certificateChain = certificateChain;
        return this;
    }

    /**
     * The list of trusted certificates used to authenticate devices.
     */
    public <T extends Certificate> LeshanServerBuilder setTrustedCertificates(T[] trustedCertificates) {
        this.trustedCertificates = trustedCertificates;
        return this;
    }

    /**
     * <p>
     * Set the {@link LwM2mNodeEncoder} which will encode {@link LwM2mNode} with supported content format.
     * </p>
     * By default the {@link DefaultLwM2mNodeEncoder} is used. It supports Text, Opaque, TLV and JSON format.
     */
    public LeshanServerBuilder setEncoder(LwM2mNodeEncoder encoder) {
        this.encoder = encoder;
        return this;
    }

    /**
     * <p>
     * Set the {@link LwM2mNodeDecoder} which will decode data in supported content format to create {@link LwM2mNode}.
     * </p>
     * By default the {@link DefaultLwM2mNodeDecoder} is used. It supports Text, Opaque, TLV and JSON format.
     */
    public LeshanServerBuilder setDecoder(LwM2mNodeDecoder decoder) {
        this.decoder = decoder;
        return this;
    }

    /**
     * Set the Californium/CoAP {@link NetworkConfig}.
     */
    public LeshanServerBuilder setCoapConfig(NetworkConfig config) {
        this.coapConfig = config;
        return this;
    }

    /**
     * Set the Scandium/DTLS Configuration : {@link DtlsConnectorConfig.Builder}.
     */
    public LeshanServerBuilder setDtlsConfig(DtlsConnectorConfig.Builder config) {
        this.dtlsConfigBuilder = config;
        return this;
    }

    /**
     * Advanced setter used to create custom CoAP endpoint.
     * <p>
     * An {@link UDPConnector} is expected for unsecured endpoint and a {@link DTLSConnector} is expected for secured
     * endpoint.
     * 
     * @param endpointFactory An {@link EndpointFactory}, you can extends {@link DefaultEndpointFactory}.
     * @return the builder for fluent Bootstrap Server creation.
     */
    public LeshanServerBuilder setEndpointFactory(EndpointFactory endpointFactory) {
        this.endpointFactory = endpointFactory;
        return this;
    }

    /**
     * deactivate unsecured CoAP endpoint
     */
    public LeshanServerBuilder disableUnsecuredEndpoint() {
        this.noUnsecuredEndpoint = true;
        return this;
    }

    /**
     * deactivate secured CoAP endpoint (DTLS)
     */
    public LeshanServerBuilder disableSecuredEndpoint() {
        this.noSecuredEndpoint = true;
        return this;
    }

    /**
     * deactivate PresenceService which tracks presence of devices using LWM2M Queue Mode. When Queue Mode is
     * deactivated request is always sent immediately and {@link ClientSleepingException} will never be raised.
     * Deactivate QueueMode can make sense if you want to handle it on your own or if you don't plan to support devices
     * with queue mode.
     */
    public LeshanServerBuilder disableQueueModeSupport() {
        this.noQueueMode = true;
        return this;
    }

    /**
     * Sets a new {@link ClientAwakeTimeProvider} object different from the default one.
     * <p>
     * By default a {@link StaticClientAwakeTimeProvider} will be used initialized with the
     * <code>MAX_TRANSMIT_WAIT</code> value available in CoAP {@link NetworkConfig} which should be by default 93s as
     * defined in <a href="https://tools.ietf.org/html/rfc7252#section-4.8.2">RFC7252</a>.
     * 
     * @param awakeTimeProvider the {@link ClientAwakeTimeProvider} to set.
     */
    public LeshanServerBuilder setClientAwakeTimeProvider(ClientAwakeTimeProvider awakeTimeProvider) {
        this.awakeTimeProvider = awakeTimeProvider;
        return this;
    }

    /**
     * Sets a new {@link RegistrationIdProvider} object different from the default one (Random string).
     * 
     * @param registrationIdProvider the {@link RegistrationIdProvider} to set.
     */
    public void setRegistrationIdProvider(RegistrationIdProvider registrationIdProvider) {
        this.registrationIdProvider = registrationIdProvider;
    }

    /**
     * The default Californium/CoAP {@link NetworkConfig} used by the builder.
     */
    public static NetworkConfig createDefaultNetworkConfig() {
        NetworkConfig networkConfig = new NetworkConfig();
        networkConfig.set(Keys.MID_TRACKER, "NULL");
        // Workaround for https://github.com/eclipse/leshan/issues/502
        // TODO remove this line when we will integrate Cf 2.0.0-M10
        // networkConfig.set(Keys.HEALTH_STATUS_INTERVAL, 0);
        return networkConfig;
    }

    public LeshanServer build() {
        if (localAddress == null)
            localAddress = new InetSocketAddress(LwM2m.DEFAULT_COAP_PORT);
        if (registrationStore == null)
            registrationStore = new InMemoryRegistrationStore();
        if (authorizer == null)
            authorizer = new DefaultAuthorizer(securityStore);
        if (modelProvider == null)
            modelProvider = new StandardModelProvider();
        if (encoder == null)
            encoder = new DefaultLwM2mNodeEncoder();
        if (decoder == null)
            decoder = new DefaultLwM2mNodeDecoder();
        if (coapConfig == null)
            coapConfig = createDefaultNetworkConfig();
        if (awakeTimeProvider == null) {
            int maxTransmitWait = coapConfig.getInt(Keys.MAX_TRANSMIT_WAIT);
            if (maxTransmitWait == 0) {
                LOG.warn(
                        "No value available for MAX_TRANSMIT_WAIT in CoAP NetworkConfig. Fallback with a default 93s value.");
                awakeTimeProvider = new StaticClientAwakeTimeProvider();
            } else {
                awakeTimeProvider = new StaticClientAwakeTimeProvider(maxTransmitWait);
            }
        }
        if (registrationIdProvider == null)
            registrationIdProvider = new RandomStringRegistrationIdProvider();
        if (endpointFactory == null) {
            endpointFactory = new DefaultEndpointFactory();
        }

        // handle dtlsConfig
        DtlsConnectorConfig dtlsConfig = null;
        if (!noSecuredEndpoint && shouldTryToCreateSecureEndpoint()) {
            if (dtlsConfigBuilder == null) {
                dtlsConfigBuilder = new DtlsConnectorConfig.Builder();
            }
            // Set default DTLS setting for Leshan unless user change it.
            DtlsConnectorConfig incompleteConfig = dtlsConfigBuilder.getIncompleteConfig();

            // Handle PSK Store
            if (incompleteConfig.getPskStore() != null) {
                LOG.warn(
                        "PskStore should be automatically set by Leshan. Using a custom implementation is not advised.");
            } else if (securityStore != null) {
                dtlsConfigBuilder.setPskStore(new LwM2mPskStore(this.securityStore, registrationStore));
            }

            // Handle secure address
            if (incompleteConfig.getAddress() == null) {
                if (localSecureAddress == null) {
                    localSecureAddress = new InetSocketAddress(LwM2m.DEFAULT_COAP_SECURE_PORT);
                }
                dtlsConfigBuilder.setAddress(localSecureAddress);
            } else if (localSecureAddress != null && !localSecureAddress.equals(incompleteConfig.getAddress())) {
                throw new IllegalStateException(String.format(
                        "Configuration conflict between LeshanBuilder and DtlsConnectorConfig.Builder for secure address: %s != %s",
                        localSecureAddress, incompleteConfig.getAddress()));
            }

            // Handle active peers
            if (incompleteConfig.getMaxConnections() == null)
                dtlsConfigBuilder.setMaxConnections(coapConfig.getInt(Keys.MAX_ACTIVE_PEERS));
            if (incompleteConfig.getStaleConnectionThreshold() == null)
                dtlsConfigBuilder.setStaleConnectionThreshold(coapConfig.getLong(Keys.MAX_PEER_INACTIVITY_PERIOD));

            // handle trusted certificates
            if (trustedCertificates != null) {
                if (incompleteConfig.getCertificateVerifier() == null) {
                    if (incompleteConfig.getTrustStore() == null) {
                        dtlsConfigBuilder.setTrustStore(trustedCertificates);
                    } else if (!Arrays.equals(trustedCertificates, incompleteConfig.getTrustStore())) {
                        throw new IllegalStateException(String.format(
                                "Configuration conflict between LeshanBuilder and DtlsConnectorConfig.Builder for trusted Certificates (trustStore) : \n%s != \n%s",
                                Arrays.toString(trustedCertificates),
                                Arrays.toString(incompleteConfig.getTrustStore())));
                    }
                }
            }

            // check conflict for private key
            if (privateKey != null) {
                if (incompleteConfig.getPrivateKey() != null && !incompleteConfig.getPrivateKey().equals(privateKey)) {
                    throw new IllegalStateException(String.format(
                            "Configuration conflict between LeshanBuilder and DtlsConnectorConfig.Builder for private key: %s != %s",
                            privateKey, incompleteConfig.getPrivateKey()));
                }

                // if in raw key mode and not in X.509 set the raw keys
                if (certificateChain == null && publicKey != null) {
                    if (incompleteConfig.getPublicKey() != null && !incompleteConfig.getPublicKey().equals(publicKey)) {
                        throw new IllegalStateException(String.format(
                                "Configuration conflict between LeshanBuilder and DtlsConnectorConfig.Builder for public key: %s != %s",
                                publicKey, incompleteConfig.getPublicKey()));
                    }

                    // by default trust all RPK
                    if (incompleteConfig.getRpkTrustStore() == null) {
                        dtlsConfigBuilder.setRpkTrustAll();
                    }
                    dtlsConfigBuilder.setIdentity(privateKey, publicKey);
                }
                // if in X.509 mode set the private key, certificate chain, public key is extracted from the certificate
                if (certificateChain != null && certificateChain.length > 0) {
                    if (incompleteConfig.getCertificateChain() != null
                            && !Arrays.asList(certificateChain).equals(incompleteConfig.getCertificateChain())) {
                        throw new IllegalStateException(String.format(
                                "Configuration conflict between LeshanBuilder and DtlsConnectorConfig.Builder for certificate chain: %s != %s",
                                Arrays.toString(certificateChain), incompleteConfig.getCertificateChain()));
                    }

                    // by default trust all RPK
                    if (incompleteConfig.getRpkTrustStore() == null) {
                        dtlsConfigBuilder.setRpkTrustAll();
                    }
                    dtlsConfigBuilder.setIdentity(privateKey, certificateChain, CertificateType.X_509,
                            CertificateType.RAW_PUBLIC_KEY);
                }
            }

            // Deactivate SNI by default
            // TODO should we support SNI ?
            if (incompleteConfig.isSniEnabled() == null) {
                dtlsConfigBuilder.setSniEnabled(false);
            }

            // we try to build the dtlsConfig, if it fail we will just not create the secured endpoint
            try {
                dtlsConfig = dtlsConfigBuilder.build();
            } catch (IllegalStateException e) {
                LOG.warn("Unable to create DTLS config and so secured endpoint.", e);
            }
        }

        // create endpoints
        CoapEndpoint unsecuredEndpoint = null;
        if (!noUnsecuredEndpoint) {
            unsecuredEndpoint = endpointFactory.createUnsecuredEndpoint(localAddress, coapConfig, registrationStore);
        }

        CoapEndpoint securedEndpoint = null;
        if (!noSecuredEndpoint && dtlsConfig != null) {
            securedEndpoint = endpointFactory.createSecuredEndpoint(dtlsConfig, coapConfig, registrationStore);
        }

        if (securedEndpoint == null && unsecuredEndpoint == null) {
            throw new IllegalStateException(
                    "All CoAP enpoints are deactivated, at least one endpoint should be activated");
        }

        return createServer(unsecuredEndpoint, securedEndpoint, registrationStore, securityStore, authorizer,
                modelProvider, encoder, decoder, coapConfig, noQueueMode, awakeTimeProvider, registrationIdProvider);
    }

    /**
     * @return true if we should try to create a secure endpoint on {@link #build()}
     */
    protected boolean shouldTryToCreateSecureEndpoint() {
        return dtlsConfigBuilder != null || certificateChain != null || privateKey != null || publicKey != null
                || securityStore != null || trustedCertificates != null;
    }

    /**
     * Create the <code>LeshanServer</code>.
     * <p>
     * You can extend <code>LeshanServerBuilder</code> and override this method to create a new builder which will be
     * able to build an extended <code>LeshanServer</code>.
     * 
     * @param unsecuredEndpoint CoAP endpoint used for <code>coap://<code> communication.
     * @param securedEndpoint CoAP endpoint used for <code>coaps://<code> communication.
     * @param registrationStore the {@link Registration} store.
     * @param securityStore the {@link SecurityInfo} store.
     * @param authorizer define which devices is allow to register on this server.
     * @param modelProvider provides the objects description for each client.
     * @param decoder decoder used to decode response payload.
     * @param encoder encode used to encode request payload.
     * @param coapConfig the CoAP {@link NetworkConfig}.
     * @param noQueueMode true to disable presenceService.
     * @param awakeTimeProvider to set the client awake time if queue mode is used.
     * @param registrationIdProvider to provide registrationId using for location-path option values on response of
     *        Register operation.
     * 
     * @return the LWM2M server
     */
    protected LeshanServer createServer(CoapEndpoint unsecuredEndpoint, CoapEndpoint securedEndpoint,
            CaliforniumRegistrationStore registrationStore, SecurityStore securityStore, Authorizer authorizer,
            LwM2mModelProvider modelProvider, LwM2mNodeEncoder encoder, LwM2mNodeDecoder decoder,
            NetworkConfig coapConfig, boolean noQueueMode, ClientAwakeTimeProvider awakeTimeProvider,
            RegistrationIdProvider registrationIdProvider) {
        return new LeshanServer(unsecuredEndpoint, securedEndpoint, registrationStore, securityStore, authorizer,
                modelProvider, encoder, decoder, coapConfig, noQueueMode, awakeTimeProvider, registrationIdProvider);
    }
}
