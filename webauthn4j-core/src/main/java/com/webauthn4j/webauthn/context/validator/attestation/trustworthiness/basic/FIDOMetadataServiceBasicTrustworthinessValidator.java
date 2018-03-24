/*
 * Copyright 2002-2018 the original author or authors.
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

package com.webauthn4j.webauthn.context.validator.attestation.trustworthiness.basic;

import com.webauthn4j.fido.metadata.FIDOMetadataServiceClient;
import com.webauthn4j.fido.metadata.Metadata;
import com.webauthn4j.fido.metadata.structure.MetadataStatement;
import com.webauthn4j.fido.metadata.structure.MetadataTOCPayload;
import com.webauthn4j.fido.metadata.structure.MetadataTOCPayloadEntry;
import com.webauthn4j.webauthn.attestation.statement.FIDOU2FAttestationStatement;
import com.webauthn4j.webauthn.attestation.statement.WebAuthnAttestationStatement;
import com.webauthn4j.webauthn.exception.CertificateException;
import com.webauthn4j.webauthn.util.CertificateUtil;

import java.security.InvalidAlgorithmParameterException;
import java.security.cert.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * FIDOMetadataServiceBasicTrustworthinessValidator
 */
public class FIDOMetadataServiceBasicTrustworthinessValidator implements BasicTrustworthinessValidator {

    private FIDOMetadataServiceClient fidoMetadataServiceClient;

    Map<TrustAnchor, Metadata> cachedMetadataMap;
    LocalDate nextUpdate;
    LocalDateTime lastRefresh;

    public FIDOMetadataServiceBasicTrustworthinessValidator(FIDOMetadataServiceClient fidoMetadataServiceClient) {
        this.fidoMetadataServiceClient = fidoMetadataServiceClient;
    }

    @Override
    public void validate(WebAuthnAttestationStatement attestationStatement) {
        Metadata metadata = findMetadata(attestationStatement);
        if (metadata == null) {
            throw new CertificateException("metadata not found");
        }
        metadata.getStatusReports().forEach(report -> {
            switch (report.getStatus()) {
                case FIDO_CERTIFIED:
                case UPDATE_AVAILABLE:
                case NOT_FIDO_CERTIFIED:
                    return;
                case ATTESTATION_KEY_COMPROMISE:
                case USER_VERIFICATION_BYPASS:
                case USER_KEY_REMOTE_COMPROMISE:
                case USER_KEY_PHYSICAL_COMPROMISE:
                case REVOKED:
                default:
                    throw new CertificateException(String.format("error response from metadata service: %s", report.getStatus()));
            }
        });
    }

    Metadata findMetadata(WebAuthnAttestationStatement attestationStatement) {
        FIDOU2FAttestationStatement fidoU2FAttestationStatement = (FIDOU2FAttestationStatement) attestationStatement;
        CertPath certPath = fidoU2FAttestationStatement.getX5c();
        Map<TrustAnchor, Metadata> metadataMap = getMetadataMap();

        Set<TrustAnchor> trustAnchors = metadataMap.keySet();

        CertPathValidator certPathValidator = CertificateUtil.generateCertPathValidator();
        PKIXParameters certPathParameters = CertificateUtil.generatePKIXParameters(trustAnchors);
        certPathParameters.setRevocationEnabled(false);

        PKIXCertPathBuilderResult result;
        try {
            result = (PKIXCertPathBuilderResult) certPathValidator.validate(certPath, certPathParameters);
        } catch (InvalidAlgorithmParameterException e) {
            throw new IllegalStateException(e);
        } catch (CertPathValidatorException e) {
            return null;
        }

        return metadataMap.get(result.getTrustAnchor());
    }

    Map<TrustAnchor, Metadata> getMetadataMap() {
        if (needsRefresh()) {
            cachedMetadataMap = refreshMetadataMap();
        }
        return cachedMetadataMap;
    }

    boolean needsRefresh() {
        return cachedMetadataMap == null || (!nextUpdate.isAfter(LocalDate.now()) && lastRefresh.isBefore(LocalDateTime.now().minusHours(1)));
    }

    Map<TrustAnchor, Metadata> refreshMetadataMap() {
        MetadataTOCPayload metadataTOC = fidoMetadataServiceClient.retrieveMetadataTOC();
        List<MetadataTOCPayloadEntry> entries = metadataTOC.getEntries();

        Map<TrustAnchor, Metadata> metadataMap = new HashMap<>();

        for (MetadataTOCPayloadEntry entry : entries) {
            MetadataStatement metadataStatement = fidoMetadataServiceClient.retrieveMetadataStatement(entry.getUrl());
            Metadata metadata = new Metadata();
            metadata.setAaid(entry.getAaid());
            metadata.setHash(entry.getHash());
            metadata.setStatusReports(entry.getStatusReports());
            metadata.setTimeOfLastStatusChange(entry.getTimeOfLastStatusChange());
            metadata.setAttestationCertificateKeyIdentifiers(entry.getAttestationCertificateKeyIdentifiers());
            metadata.setMetadataStatement(metadataStatement);
            for (X509Certificate certificate : metadataStatement.getAttestationRootCertificates()) {
                metadataMap.put(new TrustAnchor(certificate, null), metadata);
            }
        }
        nextUpdate = metadataTOC.getNextUpdate();
        lastRefresh = LocalDateTime.now();
        return metadataMap;
    }


}