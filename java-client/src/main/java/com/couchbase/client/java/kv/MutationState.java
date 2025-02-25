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
package com.couchbase.client.java.kv;

import com.couchbase.client.core.msg.kv.MutationToken;
import com.couchbase.client.java.json.JsonArray;
import com.couchbase.client.java.json.JsonObject;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;

/**
 * Aggregation of one or more {@link MutationToken MutationToken} into one {@link MutationState}.
 *
 * @author Michael Nitschinger
 * @since 2.3.0
 */
public class MutationState implements Iterable<MutationToken> {

    private final List<MutationToken> tokens;

    private MutationState() {
        this.tokens = new ArrayList<>();
    }

    /**
     * Create a {@link MutationState} from one or more {@link MutationToken MutationTokens}.
     *
     * @param mutationTokens the mutation tokens.
     * @return the initialized {@link MutationState}.
     */
    public static MutationState from(final MutationToken... mutationTokens) {
        return new MutationState().add(mutationTokens);
    }

    /**
     * Add one or more {@link MutationToken MutationTokens} to this {@link MutationState}.
     *
     * @param mutationTokens the tokens
     * @return the modified {@link MutationState}.
     */
    public MutationState add(final MutationToken... mutationTokens) {
        if (mutationTokens == null || mutationTokens.length == 0) {
            throw new IllegalArgumentException("At least one MutationToken must be provided.");
        }
        for (MutationToken t : mutationTokens) {
            addToken(t);
        }
        return this;
    }

    /**
     * Adds all the internal state from the given {@link MutationState} onto the called one.
     *
     * @param mutationState the state from which the tokens are applied from.
     * @return the modified {@link MutationState}.
     */
    public MutationState add(final MutationState mutationState) {
        for(MutationToken token : mutationState) {
            addToken(token);
        }
        return this;
    }

    /**
     * Helper method to check the incoming token and store it if needed.
     *
     * Note that the token is only stored if it doesn't exist for the given vbucket already or the given sequence
     * number is higher than the one stored.
     *
     * @param token the token to check and maybe store.
     */
    private void addToken(final MutationToken token) {
        if (token != null) {
            ListIterator<MutationToken> tokenIterator = tokens.listIterator();
            while (tokenIterator.hasNext()) {
                MutationToken t = tokenIterator.next();
                if (t.partitionID() == token.partitionID() && t.bucketName().equals(token.bucketName())) {
                    if (token.sequenceNumber() > t.sequenceNumber()) {
                        tokenIterator.set(token);
                    }
                    return;
                }
            }

            tokens.add(token);
        }
    }

    @Override
    public Iterator<MutationToken> iterator() {
        return tokens.iterator();
    }

    /**
     * Exports the {@link MutationState} into a universal format, which can be used either to serialize it into
     * a N1QL query or to send it over the network to a different application/SDK.
     *
     * @return the exported {@link JsonObject}.
     */
    public JsonObject export() {
        JsonObject result = JsonObject.create();
        for (MutationToken token : tokens) {
            JsonObject bucket = result.getObject(token.bucketName());
            if (bucket == null) {
                bucket = JsonObject.create();
                result.put(token.bucketName(), bucket);
            }

            bucket.put(
                String.valueOf(token.partitionID()),
                JsonArray.from(token.sequenceNumber(), String.valueOf(token.partitionUUID()))
            );
        }
        return result;
    }

    /**
     * Exports the {@link MutationState} into a format recognized by the FTS search engine.
     *
     * @return the exported {@link JsonObject} for one FTS index.
     */
    public JsonObject exportForSearch() {
        JsonObject result = JsonObject.create();
        for (MutationToken token : tokens) {
            String tokenKey = token.partitionID() + "/" + token.partitionUUID();
            Long seqno = result.getLong(tokenKey);
            if (seqno == null || seqno < token.sequenceNumber()) {
                result.put(tokenKey, token.sequenceNumber());
            }
        }
        return result;
    }

    /**
     * Create a {@link MutationState} from the serialized state.
     *
     * @param source the source state, serialized.
     * @return the created {@link MutationState}.
     */
    public static MutationState from(String source) {
        return from(JsonObject.fromJson(source));
    }

    /**
     * Create a {@link MutationState} from the serialized state.
     *
     * @param source the source state, serialized.
     * @return the created {@link MutationState}.
     */
    public static MutationState from(JsonObject source) {
        try {
            MutationState state = new MutationState();
            for (String bucketName : source.getNames()) {
                JsonObject bucket = source.getObject(bucketName);
                for (String vbid : bucket.getNames()) {
                    JsonArray values = bucket.getArray(vbid);
                    state.addToken(new MutationToken(
                        Short.parseShort(vbid),
                        Long.parseLong(values.getString(1)),
                        values.getLong(0),
                        bucketName
                    ));
                }
            }
            return state;
        } catch (Exception ex) {
            throw new IllegalStateException("Could not import MutationState from JSON.", ex);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        MutationState state = (MutationState) o;
        return tokens.containsAll(state.tokens) && state.tokens.containsAll(tokens);
    }

    @Override
    public int hashCode() {
        return tokens.hashCode();
    }

    @Override
    public String toString() {
        return "MutationState{tokens=" + tokens + '}';
    }
}