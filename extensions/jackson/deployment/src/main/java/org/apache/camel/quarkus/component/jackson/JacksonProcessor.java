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
package org.apache.camel.quarkus.component.jackson;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.BooleanSupplier;

import com.fasterxml.jackson.core.json.JsonWriteFeature;
import io.quarkus.bootstrap.classloading.QuarkusClassLoader;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.builditem.BytecodeTransformerBuildItem;
import io.quarkus.deployment.builditem.FeatureBuildItem;
import io.quarkus.deployment.builditem.nativeimage.ReflectiveClassBuildItem;
import io.quarkus.gizmo.Gizmo;
import org.apache.camel.component.jackson.AbstractJacksonDataFormat;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

public class JacksonProcessor {

    private static final String FEATURE = "camel-jackson";

    @BuildStep
    FeatureBuildItem feature() {
        return new FeatureBuildItem(FEATURE);
    }

    @BuildStep
    List<ReflectiveClassBuildItem> registerReflectiveClasses() {
        List<ReflectiveClassBuildItem> items = new ArrayList<>();
        items.add(
                ReflectiveClassBuildItem.builder("com.fasterxml.jackson.module.jakarta.xmlbind.JakartaXmlBindAnnotationModule")
                        .fields().build());
        items.add(ReflectiveClassBuildItem.builder("com.fasterxml.jackson.databind.JsonNode").build());
        if (QuarkusClassLoader.isClassPresentAtRuntime("com.fasterxml.jackson.datatype.joda.JodaModule")) {
            items.add(ReflectiveClassBuildItem.builder("com.fasterxml.jackson.datatype.joda.JodaModule").build());
            items.add(ReflectiveClassBuildItem.builder("org.joda.time.DateTime").build());
        }
        return items;
    }

    @BuildStep(onlyIf = JacksonCombineUnicodeSurrogatesAbsent.class)
    BytecodeTransformerBuildItem transformMethodConfigureCombineUnicodeSurrogates() {
        String message = "The combineUnicodeSurrogates option is not supported on Camel Quarkus";

        // Rewrites AbstractJacksonDataFormat.configureCombineUnicodeSurrogates to:
        //
        // protected ObjectWriter configureCombineUnicodeSurrogates(ObjectWriter objectWriter) {
        //    throw new IllegalStateException("The combineUnicodeSurrogates option is not supported on Camel Quarkus");
        // }
        return new BytecodeTransformerBuildItem.Builder()
                .setClassToTransform(AbstractJacksonDataFormat.class.getName())
                .setCacheable(true)
                .setVisitorFunction((className, classVisitor) -> {
                    return new ClassVisitor(Gizmo.ASM_API_VERSION, classVisitor) {
                        @Override
                        public MethodVisitor visitMethod(int access, String name, String descriptor, String signature,
                                String[] exceptions) {
                            MethodVisitor methodVisitor = super.visitMethod(access, name, descriptor, signature, exceptions);
                            if (name.equals("configureCombineUnicodeSurrogates")) {
                                return new MethodVisitor(Gizmo.ASM_API_VERSION, methodVisitor) {
                                    @Override
                                    public void visitCode() {
                                        super.visitCode();
                                        mv.visitLdcInsn(message);
                                        mv.visitTypeInsn(Opcodes.NEW, "java/lang/IllegalStateException");
                                        mv.visitInsn(Opcodes.DUP);
                                        mv.visitLdcInsn(message);
                                        mv.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/IllegalStateException",
                                                "<init>", "(Ljava/lang/String;)V", false);
                                        mv.visitInsn(Opcodes.ATHROW);
                                    }
                                };
                            }
                            return methodVisitor;
                        }
                    };
                })
                .build();
    }

    static final class JacksonCombineUnicodeSurrogatesAbsent implements BooleanSupplier {
        @Override
        public boolean getAsBoolean() {
            JsonWriteFeature[] enumConstants = JsonWriteFeature.class.getEnumConstants();
            return Arrays.stream(enumConstants)
                    .noneMatch(enumEntry -> enumEntry.toString().equals("COMBINE_UNICODE_SURROGATES_IN_UTF8"));
        }
    }
}
