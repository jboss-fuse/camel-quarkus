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
package org.apache.camel.quarkus.component.ftp.deployment;

import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.builditem.FeatureBuildItem;
import io.quarkus.deployment.builditem.nativeimage.ReflectiveClassBuildItem;

class FtpProcessor {

    private static final String FEATURE = "camel-ftp";

    @BuildStep
    FeatureBuildItem feature() {
        return new FeatureBuildItem(FEATURE);
    }

    @BuildStep
    ReflectiveClassBuildItem registerJSchCertificateClasses() {
        // JSch OpenSSH certificate support classes for @cert-authority parsing in known_hosts
        // and user certificate authentication. The quarkus-jsch reflection config is missing
        // several classes that JSch loads dynamically via reflection.
        return ReflectiveClassBuildItem.builder(
                // KeyPair classes for certificate verification and key type detection
                "com.jcraft.jsch.KeyPairRSA",
                "com.jcraft.jsch.KeyPairECDSA",
                "com.jcraft.jsch.KeyPairEd25519",
                "com.jcraft.jsch.KeyPairEd448",
                "com.jcraft.jsch.KeyPairDSA",
                "com.jcraft.jsch.KeyPairEdDSA",
                "com.jcraft.jsch.KeyPairPKCS8",
                // Signature classes for authentication
                "com.jcraft.jsch.SignatureRSA",
                "com.jcraft.jsch.SignatureECDSA",
                "com.jcraft.jsch.jce.SignatureEd25519",
                // Identity classes for loading private keys and certificates from files
                "com.jcraft.jsch.Identity",
                "com.jcraft.jsch.IdentityFile",
                "com.jcraft.jsch.IdentityRepository",
                "com.jcraft.jsch.LocalIdentityRepository",
                // KeyPairGen classes used internally by JSch to parse/decode private key file formats
                // (not for key generation - Camel SFTP only uses existing keys provided by the user)
                "com.jcraft.jsch.KeyPairGenRSA",
                "com.jcraft.jsch.KeyPairGenDSA",
                "com.jcraft.jsch.KeyPairGenECDSA",
                "com.jcraft.jsch.KeyPairGenEdDSA",
                "com.jcraft.jsch.jce.KeyPairGenRSA",
                "com.jcraft.jsch.jce.KeyPairGenDSA",
                "com.jcraft.jsch.jce.KeyPairGenECDSA",
                "com.jcraft.jsch.jce.KeyPairGenEdDSA")
                .build();
    }
}
