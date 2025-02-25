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
 * Subdocument exception thrown when the targeted enclosing document itself is not JSON.
 *
 * @author Simon Baslé
 * @since 2.0
 */
public class DocumentNotJsonException extends SubDocumentException {

    public DocumentNotJsonException(ErrorContext ctx, int index) {
        super("Document content is not JSON", ctx, index);
    }

}
