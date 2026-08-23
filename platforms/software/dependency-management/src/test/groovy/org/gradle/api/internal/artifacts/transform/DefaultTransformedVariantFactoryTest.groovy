/*
 * Copyright 2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gradle.api.internal.artifacts.transform

import org.gradle.api.artifacts.component.ComponentIdentifier
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ResolvedArtifactSet
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ResolvedVariant
import org.gradle.api.internal.attributes.ImmutableAttributes
import org.gradle.internal.component.external.model.ImmutableCapabilities
import org.gradle.internal.component.model.VariantIdentifier
import org.gradle.internal.component.model.VariantResolveMetadata
import org.gradle.internal.model.CalculatedValueContainerFactory
import org.gradle.internal.operations.BuildOperationRunner
import org.gradle.test.fixtures.ConcurrentTestUtil
import spock.lang.Specification

import java.lang.ref.WeakReference

class DefaultTransformedVariantFactoryTest extends Specification {

    def calculatedValueContainerFactory = Mock(CalculatedValueContainerFactory)
    def factory = new DefaultTransformedVariantFactory(Stub(BuildOperationRunner), calculatedValueContainerFactory, Stub(TransformStepNodeFactory))

    def sourceVariantId = Stub(VariantIdentifier)
    def variantIdentifier = Stub(VariantResolveMetadata.Identifier)
    def sourceVariant = Stub(ResolvedVariant) {
        getIdentifier() >> variantIdentifier
        getSourceVariantId() >> sourceVariantId
        getArtifacts() >> Stub(ResolvedArtifactSet)
        getCapabilities() >> ImmutableCapabilities.EMPTY
    }
    def targetAttributes = ImmutableAttributes.EMPTY
    def componentIdentifier = Stub(ComponentIdentifier)

    private ResolvedArtifactSet transformForComponent() {
        def variantDefinition = Stub(VariantDefinition) {
            getTargetAttributes() >> targetAttributes
            getTransformChain() >> Stub(TransformChain) {
                requiresDependencies() >> false
            }
        }
        return factory.transformedExternalArtifacts(componentIdentifier, sourceVariant, variantDefinition, Stub(TransformUpstreamDependenciesResolver))
    }

    private WeakReference<ResolvedArtifactSet> transformAndKeepOnlyAWeakReference() {
        return new WeakReference<>(transformForComponent())
    }

    def "reuses the transformed artifact set for the same variant"() {
        when:
        def first = transformForComponent()
        def second = transformForComponent()

        then:
        first.is(second)
    }

    def "does not retain transformed artifact sets once they are no longer used"() {
        given:
        WeakReference<ResolvedArtifactSet> reference = transformAndKeepOnlyAWeakReference()

        expect:
        ConcurrentTestUtil.poll(10) {
            System.gc()
            reference.get() == null
        }

        when:
        def recreated = transformForComponent()

        then:
        recreated != null
    }
}
