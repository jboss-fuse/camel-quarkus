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
package org.apache.camel.quarkus.component.sap.graal;

import java.io.File;
import java.lang.reflect.Constructor;

import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;
import org.apache.camel.quarkus.component.sap.DsrAbsent;
import org.apache.camel.quarkus.component.sap.JarmAbsent;

final class JCoSubstitutions {
}

@TargetClass(className = "com.sap.conn.jco.rt.DefaultJCoRuntime")
final class SubstituteDefaultJCoRuntime {

    @Substitute
    private static String loadJCoLibrary() {
        String libName = "sapjco3";
        String libPath = System.getProperty("java.library.path");
        if (libPath != null) {
            for (String dir : libPath.split(File.pathSeparator)) {
                File libFile = new File(dir, System.mapLibraryName(libName));
                if (libFile.isFile()) {
                    System.load(libFile.getAbsolutePath());
                    return libFile.getAbsolutePath();
                }
            }
        }
        System.loadLibrary(libName);
        return "System-defined path to " + System.mapLibraryName(libName);
    }
}

@TargetClass(className = "com.sap.conn.jco.rt.ConnectionManager")
final class SubstituteConnectionManager {
}

/**
 * {@code JCoRuntime.createConnectionManager()} uses {@code Class.newInstance()}
 * which cannot access the {@code protected} constructor of
 * {@code DefaultConnectionManager} in native mode. This substitution uses
 * {@code getDeclaredConstructor()} with {@code setAccessible(true)} instead.
 */
@TargetClass(className = "com.sap.conn.jco.rt.JCoRuntime")
final class SubstituteJCoRuntime {

    @Substitute
    protected SubstituteConnectionManager createConnectionManager() {
        try {
            boolean inNeo = (boolean) Class.forName("com.sap.conn.jco.ext.Environment")
                    .getMethod("inNeo").invoke(null);
            String className = inNeo
                    ? "com.sap.conn.jco.rt.NeoConnectionManager"
                    : "com.sap.conn.jco.rt.DefaultConnectionManager";
            Class<?> clazz = Class.forName(className);
            Constructor<?> ctor = clazz.getDeclaredConstructor();
            ctor.setAccessible(true);
            return (SubstituteConnectionManager) ctor.newInstance();
        } catch (Exception e) {
            throw new RuntimeException("createConnectionManager() failed", e);
        }
    }
}

/**
 * DSR/JARM are optional SAP monitoring libraries not included in the standard
 * JCo distribution. In native mode, stub classes satisfy GraalVM's type
 * resolution but should not be treated as real implementations. These
 * substitutions make JCo behave as if the libraries are absent, matching
 * JVM mode behavior.
 */
@TargetClass(className = "com.sap.conn.jco.util.Dsr", onlyWith = DsrAbsent.class)
final class SubstituteDsr {

    @Substitute
    private static boolean initDsr() {
        return false;
    }
}

@TargetClass(className = "com.sap.conn.jco.util.Jarm", onlyWith = JarmAbsent.class)
final class SubstituteJarm {

    @Substitute
    public static boolean isInClasspath() {
        return false;
    }
}
