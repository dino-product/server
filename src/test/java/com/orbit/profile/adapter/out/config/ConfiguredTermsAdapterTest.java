package com.orbit.profile.adapter.out.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URI;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.orbit.profile.domain.TermsDocument;
import com.orbit.profile.domain.TermsDocumentType;
import com.orbit.profile.domain.TermsType;

@DisplayName("설정값 약관 어댑터")
class ConfiguredTermsAdapterTest {

    private static final TermsVersionProperties VERSIONS =
            new TermsVersionProperties("2026-10-10", "2026-10-11", "2026-10-12", "2026-10-13");

    @Test
    @DisplayName("약관 문서마다 버전과 {주소}/{종류}/{버전}.txt 본문 주소를 정해진 순서로 돌려준다")
    void buildsDocumentUrlsInTypeOrder() {
        ConfiguredTermsAdapter adapter =
                new ConfiguredTermsAdapter(VERSIONS, new TermsLocationProperties("https://dev.orbi-t.com/terms"));

        assertThat(adapter.currentDocuments())
                .containsExactly(
                        document(TermsDocumentType.SERVICE, "2026-10-10", "service"),
                        document(TermsDocumentType.PRIVACY, "2026-10-11", "privacy-consent"),
                        document(TermsDocumentType.MARKETING, "2026-10-12", "marketing"),
                        document(TermsDocumentType.PRIVACY_POLICY, "2026-10-13", "privacy-policy"));
        assertThat(adapter.currentVersions().versionOf(TermsType.PRIVACY)).isEqualTo("2026-10-11");
    }

    @Test
    @DisplayName("주소 끝의 / 는 한 번만 붙는다")
    void ignoresTrailingSlashOfBaseUrl() {
        ConfiguredTermsAdapter adapter =
                new ConfiguredTermsAdapter(VERSIONS, new TermsLocationProperties("https://orbi-t.com/terms/"));

        assertThat(adapter.currentDocuments().getFirst().url())
                .isEqualTo(URI.create("https://orbi-t.com/terms/service/2026-10-10.txt"));
    }

    @Test
    @DisplayName("처리방침 버전이나 본문 주소가 비어 있거나 상대 주소면 기동하지 않는다")
    void rejectsMissingPolicyVersionOrBaseUrl() {
        TermsVersionProperties noPolicy = new TermsVersionProperties("2026-10-10", "2026-10-10", "2026-10-10", " ");

        assertThatThrownBy(
                        () -> new ConfiguredTermsAdapter(noPolicy, new TermsLocationProperties("https://orbi-t.com")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ConfiguredTermsAdapter(VERSIONS, new TermsLocationProperties(null)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ConfiguredTermsAdapter(VERSIONS, new TermsLocationProperties("/terms")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("버전이 시행일 형식이 아니면 주소가 깨지므로 기동하지 않는다")
    void rejectsVersionThatIsNotEffectiveDate() {
        TermsVersionProperties fragment =
                new TermsVersionProperties("2026-10-10#a", "2026-10-10", "2026-10-10", "2026-10-10");
        TermsVersionProperties path = new TermsVersionProperties("2026-10-10", "2026-10-10", "v1/2", "2026-10-10");
        TermsLocationProperties location = new TermsLocationProperties("https://orbi-t.com/terms");

        assertThatThrownBy(() -> new ConfiguredTermsAdapter(fragment, location))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ConfiguredTermsAdapter(path, location))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static TermsDocument document(TermsDocumentType type, String version, String path) {
        return new TermsDocument(
                type, version, URI.create("https://dev.orbi-t.com/terms/" + path + "/" + version + ".txt"));
    }
}
