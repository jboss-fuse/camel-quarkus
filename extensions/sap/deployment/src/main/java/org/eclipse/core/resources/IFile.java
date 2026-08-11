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
package org.eclipse.core.resources;

import java.io.InputStream;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.content.IContentDescription;

/**
 * Stub for GraalVM native-image type resolution. Injected only when the real library is absent.
 */
public interface IFile extends IResource {

    InputStream getContents() throws CoreException;

    String getCharset() throws CoreException;

    IContentDescription getContentDescription() throws CoreException;

    IProject getProject();

    boolean isSynchronized(int depth);

    void refreshLocal(int depth, IProgressMonitor monitor) throws CoreException;

    void delete(boolean force, IProgressMonitor monitor) throws CoreException;
}
