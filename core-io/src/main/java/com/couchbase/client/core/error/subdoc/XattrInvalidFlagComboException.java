/*
 * Copyright (c) 2016 Couchbase, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.couchbase.client.core.error.subdoc;


import com.couchbase.client.core.error.ErrorContext;

/**
 * Subdocument exception thrown when a macro has been requested when not using extended attributes.
 *
 * @author Graham Pople
 * @since 2.0
 */
public class XattrInvalidFlagComboException extends SubDocumentException {

    public XattrInvalidFlagComboException(ErrorContext ctx, int index) {
        super("Need to specify xattr flag when requesting macro for path", ctx, index);
    }

}
