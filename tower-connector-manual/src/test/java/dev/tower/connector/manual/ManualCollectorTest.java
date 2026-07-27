package dev.tower.connector.manual;

import dev.tower.domain.observation.ObservationSource;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ManualCollectorTest {

    @Test
    void stamps_manual_provenance_naming_the_actor() {
        ObservationSource source = new ManualCollector("costin").source();

        assertThat(source.isManual()).isTrue();
        assertThat(source.collector()).isEqualTo(ObservationSource.MANUAL);
        assertThat(source.actor()).isEqualTo("costin");
    }

    /**
     * A missing OS user must not produce provenance that silently claims nobody
     * entered the fact. Recording it as unknown is honest; recording it as null
     * would look like an automated Collector.
     */
    @Test
    void an_unresolvable_actor_is_recorded_as_unknown_rather_than_absent() {
        assertThat(new ManualCollector(null).source().actor()).isEqualTo(ManualCollector.UNKNOWN_ACTOR);
        assertThat(new ManualCollector("   ").source().actor()).isEqualTo(ManualCollector.UNKNOWN_ACTOR);
    }
}
