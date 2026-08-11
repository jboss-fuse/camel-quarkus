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
package org.apache.camel.quarkus.component.sap.deployment;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;

import io.quarkus.bootstrap.classloading.QuarkusClassLoader;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.builditem.CombinedIndexBuildItem;
import io.quarkus.deployment.builditem.FeatureBuildItem;
import io.quarkus.deployment.builditem.GeneratedNativeImageClassBuildItem;
import io.quarkus.deployment.builditem.IndexDependencyBuildItem;
import io.quarkus.deployment.builditem.nativeimage.ReflectiveClassBuildItem;
import io.quarkus.deployment.builditem.nativeimage.RuntimeInitializedClassBuildItem;
import io.quarkus.deployment.pkg.steps.NativeOrNativeSourcesBuild;
import org.jboss.jandex.IndexView;

class SapProcessor {

    private static final String FEATURE = "camel-sap";

    private static final String[] RUNTIME_INIT_PACKAGE_PREFIXES = {
            "com.sap.conn.jco.",
            "com.sap.conn.rfc.",
            "com.sap.i18n.",
            "com.sap.conn.idoc.jco.",
            "org.fusesource.camel.component.sap.util.",
            "org.eclipse.emf.edit.command.",
    };

    private static final String[] DSR_STUB_CLASSES = {
            "com.sap.jdsr.writer.DsrFactory",
            "com.sap.jdsr.writer.DsrIMainRecord",
            "com.sap.jdsr.writer.DsrIPassport",
            "com.sap.jdsr.writer.DsrIRecordSet",
            "com.sap.jdsr.writer.DsrISubRecordCert",
    };

    private static final String[] JARM_STUB_CLASSES = {
            "com.sap.util.monitor.jarm.IMonitor",
            "com.sap.util.monitor.jarm.TaskMonitor",
    };

    private static final String[] ECLIPSE_STUB_CLASSES = {
            "org.eclipse.core.resources.IContainer",
            "org.eclipse.core.resources.IFile",
            "org.eclipse.core.resources.IProject",
            "org.eclipse.core.resources.IResource",
            "org.eclipse.core.resources.IWorkspaceRoot",
            "org.eclipse.core.resources.ProjectScope",
            "org.eclipse.core.resources.ResourceAttributes",
    };

    @BuildStep
    FeatureBuildItem feature() {
        return new FeatureBuildItem(FEATURE);
    }

    @BuildStep(onlyIf = NativeOrNativeSourcesBuild.class)
    void indexDependencies(BuildProducer<IndexDependencyBuildItem> indexDeps) {
        indexDeps.produce(new IndexDependencyBuildItem("com.sap.conn.jco", "sapjco3"));
        indexDeps.produce(new IndexDependencyBuildItem("com.sap.conn.idoc", "sapidoc3"));
        indexDeps.produce(new IndexDependencyBuildItem("org.fusesource", "camel-sap"));
        indexDeps.produce(new IndexDependencyBuildItem("org.eclipse.emf", "org.eclipse.emf.edit"));
    }

    @BuildStep(onlyIf = NativeOrNativeSourcesBuild.class)
    void runtimeInitializedClasses(
            CombinedIndexBuildItem combinedIndex,
            BuildProducer<RuntimeInitializedClassBuildItem> runtimeInitializedClasses) {

        IndexView index = combinedIndex.getIndex();
        index.getKnownClasses().stream()
                .map(ci -> ci.name().toString())
                .filter(name -> Arrays.stream(RUNTIME_INIT_PACKAGE_PREFIXES).anyMatch(name::startsWith))
                .forEach(name -> runtimeInitializedClasses
                        .produce(new RuntimeInitializedClassBuildItem(name)));

        runtimeInitializedClasses.produce(new RuntimeInitializedClassBuildItem("org.eclipse.emf.common.util.Diagnostic"));
    }

    @BuildStep(onlyIf = NativeOrNativeSourcesBuild.class)
    void reflectiveClasses(
            CombinedIndexBuildItem combinedIndex,
            BuildProducer<ReflectiveClassBuildItem> reflectiveClasses) {

        reflectiveClasses.produce(ReflectiveClassBuildItem.builder(
                "com.sap.conn.jco.rt.JCoRuntimeFactory",
                "com.sap.conn.jco.rt.DefaultJCoRuntime",
                "com.sap.conn.jco.rt.DefaultConnectionManager",
                "com.sap.conn.jco.rt.StandaloneServerFactory",
                "com.sap.conn.jco.rt.About",
                "com.sap.conn.jco.rt.RuntimeEnvironment")
                .methods(true).build());

        IndexView index = combinedIndex.getIndex();
        index.getKnownClasses().stream()
                .map(ci -> ci.name().toString())
                .filter(name -> name.startsWith("org.fusesource.camel.component.sap.model.") && name.endsWith("Impl"))
                .forEach(name -> reflectiveClasses
                        .produce(ReflectiveClassBuildItem.builder(name + "[]").build()));
    }

    @BuildStep(onlyIf = NativeOrNativeSourcesBuild.class)
    void injectStubClasses(BuildProducer<GeneratedNativeImageClassBuildItem> generatedNativeImageClasses) {
        injectStubsIfAbsent(generatedNativeImageClasses, DSR_STUB_CLASSES, "com.sap.jdsr.writer.DsrFactory");
        injectStubsIfAbsent(generatedNativeImageClasses, JARM_STUB_CLASSES, "com.sap.util.monitor.jarm.IMonitor");
        injectStubsIfAbsent(generatedNativeImageClasses, ECLIPSE_STUB_CLASSES, "org.eclipse.core.resources.IResource");
    }

    private static boolean isClassAvailable(String className) {
        return QuarkusClassLoader.isClassPresentAtRuntime(className);
    }

    private void injectStubsIfAbsent(
            BuildProducer<GeneratedNativeImageClassBuildItem> producer,
            String[] stubClasses,
            String detectClass) {
        if (isClassAvailable(detectClass)) {
            return;
        }
        for (String className : stubClasses) {
            String resourcePath = className.replace('.', '/') + ".class";
            try (InputStream is = Thread.currentThread().getContextClassLoader().getResourceAsStream(resourcePath)) {
                if (is != null) {
                    producer.produce(new GeneratedNativeImageClassBuildItem(className, is.readAllBytes()));
                }
            } catch (IOException e) {
                throw new RuntimeException("Failed to read stub class: " + className, e);
            }
        }
    }
}
