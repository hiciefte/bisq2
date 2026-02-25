/*
 * This file is part of Bisq.
 *
 * Bisq is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * Bisq is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Bisq. If not, see <http://www.gnu.org/licenses/>.
 */

package bisq.api.web_socket.subscription;

import bisq.common.json.JsonMapperProvider;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.Optional;

public class SubscriptionRequestParser {
    public boolean canParse(String json) {
        return parse(json).isPresent();
    }

    public Optional<SubscriptionRequest> parse(String json) {
        try {
            JsonNode node = JsonMapperProvider.get().readTree(json);
            if (node == null || !node.isObject()) {
                return Optional.empty();
            }

            JsonNode requestIdNode = node.get("requestId");
            JsonNode topicNode = node.get("topic");
            if (requestIdNode == null || topicNode == null) {
                return Optional.empty();
            }

            JsonNode typeNode = node.get("type");
            JsonNode requestTypeNode = node.get("requestType");
            boolean hasTypedShape = typeNode != null && "SubscriptionRequest".equals(typeNode.asText());
            boolean hasPythonShape = requestTypeNode != null && "Subscribe".equals(requestTypeNode.asText());
            boolean hasLegacyShape = typeNode == null && requestTypeNode == null;
            if (!(hasTypedShape || hasPythonShape || hasLegacyShape)) {
                return Optional.empty();
            }

            Topic topic = Topic.valueOf(topicNode.asText());
            JsonNode parameterNode = node.get("parameter");
            String parameter = parameterNode != null && !parameterNode.isNull()
                    ? parameterNode.asText()
                    : null;
            return Optional.of(new SubscriptionRequest(requestIdNode.asText(), topic, parameter));
        } catch (Exception ignored) {
            return Optional.empty();
        }
    }
}
