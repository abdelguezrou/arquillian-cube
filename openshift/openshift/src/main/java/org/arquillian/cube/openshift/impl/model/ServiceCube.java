package org.arquillian.cube.openshift.impl.model;

import static org.arquillian.cube.openshift.impl.client.ResourceUtil.toBinding;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.ServicePort;

import java.net.InetAddress;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import org.arquillian.cube.openshift.impl.client.CubeOpenShiftConfiguration;
import org.arquillian.cube.openshift.impl.client.OpenShiftClient;
import org.arquillian.cube.spi.BaseCube;
import org.arquillian.cube.spi.Binding;
import org.arquillian.cube.spi.CubeControlException;
import org.arquillian.cube.spi.metadata.HasPortBindings;

public class ServiceCube extends BaseCube<Void> {

    private String id;
    private Service resource;
    private State state;
    private CubeOpenShiftConfiguration configuration;
    private OpenShiftClient client;

    private final PortBindings portBindings;

    public ServiceCube(Service resource, OpenShiftClient client, CubeOpenShiftConfiguration configuration) {
        this.id = resource.getMetadata().getName();
        this.resource = resource;
        this.client = client;
        this.configuration = configuration;
        this.portBindings = new PortBindings();
        addDefaultMetadata();
    }

    private void addDefaultMetadata() {
        addMetadata(HasPortBindings.class, this.portBindings);
    }

    @Override
    public State state() {
        return state;
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public void create() throws CubeControlException {
        this.state = State.CREATED;
    }

    @Override
    public void start() throws CubeControlException {
        try {
            resource = client.create(resource);
            portBindings.serviceStarted();
            this.state = State.STARTED;
        } catch (Exception e) {
            this.state = State.START_FAILED;
            throw CubeControlException.failedStart(getId(), e);
        }
    }

    @Override
    public void stop() throws CubeControlException {
        try {
            client.destroy(resource);
            this.state = State.STOPPED;
        } catch (Exception e) {
            this.state = State.STOP_FAILED;
            throw CubeControlException.failedStop(getId(), e);
        }
    }

    @Override
    public void destroy() throws CubeControlException {
        this.state = State.DESTROYED;
    }

    @Override
    public boolean isRunningOnRemote() {
        return state == State.STARTED;
    }

    @Override
    public void changeToPreRunning() {
        // TODO Auto-generated method stub

    }

    @Override
    public Binding bindings() {
        return toBinding(resource);
    }

    @Override
    public Binding configuredBindings() {
        return toBinding(resource);
    }

    @Override
    public Void configuration() {
        return null;
    }
    private final class PortBindings implements HasPortBindings {

        private final Map<Integer, PortAddress> mappedPorts;
        private final Set<Integer> containerPorts;
        private String containerIP;

        public PortBindings() {
            this.mappedPorts = new HashMap<Integer, PortAddress>();
            this.containerPorts = new LinkedHashSet<Integer>();
            for (ServicePort servicePort : resource.getSpec().getPorts()) {
                final int port = servicePort.getPort();
                final Integer nodePort = servicePort.getNodePort();
                containerPorts.add(port);
                if (nodePort != null) {
                    mappedPorts.put(port, new PortAddressImpl(containerIP, nodePort));
                }
            }
        }

        @Override
        public boolean isBound() {
            return state == State.STARTED;
        }

        @Override
        public synchronized String getContainerIP() {
            return containerIP;
        }

        @Override
        public String getInternalIP() {
            return null;
        }

        @Override
        public Set<Integer> getContainerPorts() {
            return Collections.unmodifiableSet(containerPorts);
        }

        @Override
        public Set<Integer> getBoundPorts() {
            // no difference between these
            return Collections.unmodifiableSet(containerPorts);
        }

        @Override
        public synchronized PortAddress getMappedAddress(int targetPort) {
            if (mappedPorts.containsKey(targetPort)) {
                return mappedPorts.get(targetPort);
            }
            return null;
        }

        @Override
        public InetAddress getPortForwardBindAddress() {
            return null;
        }

        private synchronized void serviceStarted() throws Exception {
            containerIP = resource.getSpec().getClusterIP();
            for (ServicePort servicePort : resource.getSpec().getPorts()) {
                final int port = servicePort.getPort();
                final Integer nodePort = servicePort.getNodePort();
                containerPorts.add(port);
                if (nodePort != null) {
                    // overwrite whatever's there as the containerIP has just been set
                    mappedPorts.put(port, new PortAddressImpl(containerIP, nodePort));
                }
            }
        }
    }
}
