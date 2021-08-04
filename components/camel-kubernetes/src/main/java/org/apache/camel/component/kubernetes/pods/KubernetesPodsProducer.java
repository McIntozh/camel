/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.camel.component.kubernetes.pods;

import java.util.Map;

import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.PodBuilder;
import io.fabric8.kubernetes.api.model.PodList;
import io.fabric8.kubernetes.api.model.PodSpec;
import io.fabric8.kubernetes.client.dsl.FilterWatchListMultiDeletable;
import org.apache.camel.Exchange;
import org.apache.camel.component.kubernetes.AbstractKubernetesEndpoint;
import org.apache.camel.component.kubernetes.KubernetesConstants;
import org.apache.camel.component.kubernetes.KubernetesHelper;
import org.apache.camel.component.kubernetes.KubernetesOperations;
import org.apache.camel.support.DefaultProducer;
import org.apache.camel.support.MessageHelper;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class KubernetesPodsProducer extends DefaultProducer {

    private static final Logger LOG = LoggerFactory.getLogger(KubernetesPodsProducer.class);

    public KubernetesPodsProducer(AbstractKubernetesEndpoint endpoint) {
        super(endpoint);
    }

    @Override
    public AbstractKubernetesEndpoint getEndpoint() {
        return (AbstractKubernetesEndpoint) super.getEndpoint();
    }

    @Override
    public void process(Exchange exchange) throws Exception {
        String operation = KubernetesHelper.extractOperation(getEndpoint(), exchange);

        switch (operation) {

            case KubernetesOperations.LIST_PODS_OPERATION:
                doList(exchange);
                break;

            case KubernetesOperations.LIST_PODS_BY_LABELS_OPERATION:
                doListPodsByLabel(exchange);
                break;

            case KubernetesOperations.GET_POD_OPERATION:
                doGetPod(exchange);
                break;

            case KubernetesOperations.CREATE_POD_OPERATION:
                doCreatePod(exchange);
                break;

            case KubernetesOperations.DELETE_POD_OPERATION:
                doDeletePod(exchange);
                break;

            default:
                throw new IllegalArgumentException("Unsupported operation " + operation);
        }
    }

    protected void doList(Exchange exchange) {
        PodList podList;
        String namespaceName = exchange.getIn().getHeader(KubernetesConstants.KUBERNETES_NAMESPACE_NAME, String.class);
        if (ObjectHelper.isNotEmpty(namespaceName)) {
            podList = getEndpoint().getKubernetesClient().pods().inNamespace(namespaceName).list();
        } else {
            podList = getEndpoint().getKubernetesClient().pods().inAnyNamespace().list();
        }
        MessageHelper.copyHeaders(exchange.getIn(), exchange.getOut(), true);
        exchange.getOut().setBody(podList.getItems());
    }

    protected void doListPodsByLabel(Exchange exchange) {
        Map<String, String> labels = exchange.getIn().getHeader(KubernetesConstants.KUBERNETES_PODS_LABELS, Map.class);
        if (ObjectHelper.isEmpty(labels)) {
            LOG.error("Get pods by labels require specify a labels set");
            throw new IllegalArgumentException("Get pods by labels require specify a labels set");
        }

        FilterWatchListMultiDeletable<Pod, PodList> pods = getEndpoint().getKubernetesClient().pods().inAnyNamespace();
        for (Map.Entry<String, String> entry : labels.entrySet()) {
            pods.withLabel(entry.getKey(), entry.getValue());
        }
        PodList podList = pods.list();

        MessageHelper.copyHeaders(exchange.getIn(), exchange.getOut(), true);
        exchange.getOut().setBody(podList.getItems());
    }

    protected void doGetPod(Exchange exchange) {
        Pod pod = null;
        String podName = exchange.getIn().getHeader(KubernetesConstants.KUBERNETES_POD_NAME, String.class);
        String namespaceName = exchange.getIn().getHeader(KubernetesConstants.KUBERNETES_NAMESPACE_NAME, String.class);
        if (ObjectHelper.isEmpty(podName)) {
            LOG.error("Get a specific pod require specify a pod name");
            throw new IllegalArgumentException("Get a specific pod require specify a pod name");
        }
        if (ObjectHelper.isEmpty(namespaceName)) {
            LOG.error("Get a specific pod require specify a namespace name");
            throw new IllegalArgumentException("Get a specific pod require specify a namespace name");
        }
        pod = getEndpoint().getKubernetesClient().pods().inNamespace(namespaceName).withName(podName).get();

        MessageHelper.copyHeaders(exchange.getIn(), exchange.getOut(), true);
        exchange.getOut().setBody(pod);
    }

    protected void doCreatePod(Exchange exchange) {
        Pod pod = null;
        String podName = exchange.getIn().getHeader(KubernetesConstants.KUBERNETES_POD_NAME, String.class);
        String namespaceName = exchange.getIn().getHeader(KubernetesConstants.KUBERNETES_NAMESPACE_NAME, String.class);
        PodSpec podSpec = exchange.getIn().getHeader(KubernetesConstants.KUBERNETES_POD_SPEC, PodSpec.class);
        if (ObjectHelper.isEmpty(podName)) {
            LOG.error("Create a specific pod require specify a pod name");
            throw new IllegalArgumentException("Create a specific pod require specify a pod name");
        }
        if (ObjectHelper.isEmpty(namespaceName)) {
            LOG.error("Create a specific pod require specify a namespace name");
            throw new IllegalArgumentException("Create a specific pod require specify a namespace name");
        }
        if (ObjectHelper.isEmpty(podSpec)) {
            LOG.error("Create a specific pod require specify a pod spec bean");
            throw new IllegalArgumentException("Create a specific pod require specify a pod spec bean");
        }
        Map<String, String> labels = exchange.getIn().getHeader(KubernetesConstants.KUBERNETES_PODS_LABELS, Map.class);
        Pod podCreating = new PodBuilder().withNewMetadata().withName(podName).withLabels(labels).endMetadata()
                .withSpec(podSpec).build();
        pod = getEndpoint().getKubernetesClient().pods().inNamespace(namespaceName).create(podCreating);

        MessageHelper.copyHeaders(exchange.getIn(), exchange.getOut(), true);
        exchange.getOut().setBody(pod);
    }

    protected void doDeletePod(Exchange exchange) {
        String podName = exchange.getIn().getHeader(KubernetesConstants.KUBERNETES_POD_NAME, String.class);
        String namespaceName = exchange.getIn().getHeader(KubernetesConstants.KUBERNETES_NAMESPACE_NAME, String.class);
        if (ObjectHelper.isEmpty(podName)) {
            LOG.error("Delete a specific pod require specify a pod name");
            throw new IllegalArgumentException("Delete a specific pod require specify a pod name");
        }
        if (ObjectHelper.isEmpty(namespaceName)) {
            LOG.error("Delete a specific pod require specify a namespace name");
            throw new IllegalArgumentException("Delete a specific pod require specify a namespace name");
        }
        boolean podDeleted = getEndpoint().getKubernetesClient().pods().inNamespace(namespaceName).withName(podName).delete();

        MessageHelper.copyHeaders(exchange.getIn(), exchange.getOut(), true);
        exchange.getOut().setBody(podDeleted);
    }
}
