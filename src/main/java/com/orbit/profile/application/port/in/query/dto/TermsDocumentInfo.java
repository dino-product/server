package com.orbit.profile.application.port.in.query.dto;

import java.net.URI;

import com.orbit.profile.domain.TermsDocumentType;

public record TermsDocumentInfo(TermsDocumentType type, String version, URI url) {}
