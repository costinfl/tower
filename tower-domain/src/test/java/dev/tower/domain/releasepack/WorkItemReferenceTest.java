package dev.tower.domain.releasepack;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import dev.tower.domain.shared.DomainConflictException;
import dev.tower.domain.shared.DomainException;

/**
 * ADR-018: a work item reference is Intent, not an Observation.
 *
 * <p>What is under test is mostly what Tower refuses to do — resolve, correct or
 * duplicate a reference — because the value of this type is in what it does not
 * hold.
 */
@DisplayName("Work items a release claims to deliver")
class WorkItemReferenceTest {

    @Nested
    @DisplayName("names an item without owning it")
    class Naming {

        @Test
        void carries_the_identifier_as_the_tracker_writes_it() {
            // Not parsed, not normalised into a Tower vocabulary: that is what
            // lets the tracker be replaced without touching the Domain Model.
            assertThat(WorkItemReference.of("PROJ-123").identifier()).isEqualTo("PROJ-123");
            assertThat(WorkItemReference.of("#42").identifier()).isEqualTo("#42");
            assertThat(WorkItemReference.of("  GH-7  ").identifier()).isEqualTo("GH-7");
        }

        @Test
        void may_be_linked_before_any_title_is_known() {
            // A reference can be made before a Connector is configured, or to a
            // tracker Tower cannot reach. The reference is the claim; the title
            // is a convenience.
            WorkItemReference reference = WorkItemReference.of("PROJ-123");

            assertThat(reference.hasTitle()).isFalse();
            assertThat(reference.title()).isEmpty();
        }

        @Test
        void refuses_a_reference_that_names_nothing() {
            assertThatThrownBy(() -> WorkItemReference.of("  "))
                    .isInstanceOf(DomainException.class);
        }

        @Test
        void accepting_a_title_keeps_the_same_item() {
            WorkItemReference accepted = WorkItemReference.of("PROJ-123").withTitle("Save basket");

            assertThat(accepted.identifier()).isEqualTo("PROJ-123");
            assertThat(accepted.title()).isEqualTo("Save basket");
            assertThat(accepted.hasTitle()).isTrue();
        }

        @Test
        void the_same_item_in_different_case_is_the_same_item() {
            // Trackers are case-insensitive about keys, and "proj-123" is not a
            // second ticket.
            assertThat(WorkItemReference.of("PROJ-123")
                    .namesSameItemAs(WorkItemReference.of("proj-123"))).isTrue();
        }

        @Test
        void a_newer_title_does_not_make_it_a_different_item() {
            assertThat(WorkItemReference.of("PROJ-123").withTitle("Old wording")
                    .namesSameItemAs(WorkItemReference.of("PROJ-123").withTitle("New wording")))
                    .isTrue();
        }
    }

    @Nested
    @DisplayName("on a Release Pack")
    class OnAPack {

        private ReleasePack pack() {
            return ReleasePack.create("Release 2026.08", "");
        }

        @Test
        void links_what_the_release_delivers() {
            ReleasePack linked = pack()
                    .linkWorkItem(WorkItemReference.of("PROJ-123").withTitle("Save basket"))
                    .linkWorkItem(WorkItemReference.of("PROJ-140"));

            assertThat(linked.workItems())
                    .extracting(WorkItemReference::identifier)
                    .containsExactly("PROJ-123", "PROJ-140");
        }

        @Test
        void refuses_to_deliver_the_same_item_twice() {
            ReleasePack linked = pack().linkWorkItem(WorkItemReference.of("PROJ-123"));

            assertThatThrownBy(() -> linked.linkWorkItem(
                    WorkItemReference.of("proj-123").withTitle("A newer summary")))
                    .isInstanceOf(DomainConflictException.class)
                    .hasMessageContaining("PROJ-123");
        }

        @Test
        void accepts_a_title_for_an_item_already_linked() {
            // Separate from linking, because accepting a title is a decision:
            // the developer has seen what the tracker says and takes it as
            // Tower's own.
            ReleasePack updated = pack()
                    .linkWorkItem(WorkItemReference.of("PROJ-123"))
                    .acceptWorkItemTitle("PROJ-123", "Save basket");

            assertThat(updated.workItems()).singleElement()
                    .extracting(WorkItemReference::title).isEqualTo("Save basket");
        }

        @Test
        void refuses_a_title_for_an_item_the_release_does_not_deliver() {
            // Accepting a title for an unlinked item would be inventing the link.
            assertThatThrownBy(() -> pack().acceptWorkItemTitle("PROJ-999", "Something else"))
                    .isInstanceOf(DomainConflictException.class);
        }

        @Test
        void withdraws_a_claim_when_asked() {
            ReleasePack updated = pack()
                    .linkWorkItem(WorkItemReference.of("PROJ-123"))
                    .linkWorkItem(WorkItemReference.of("PROJ-140"))
                    .unlinkWorkItem("proj-123");

            assertThat(updated.workItems())
                    .extracting(WorkItemReference::identifier)
                    .containsExactly("PROJ-140");
        }

        @Test
        void refuses_to_unlink_what_was_never_linked() {
            assertThatThrownBy(() -> pack().unlinkWorkItem("PROJ-999"))
                    .isInstanceOf(DomainConflictException.class);
        }

        @Test
        void an_archived_release_no_longer_changes_what_it_claims_to_deliver() {
            ReleasePack archived = pack().linkWorkItem(WorkItemReference.of("PROJ-123")).archive();

            assertThatThrownBy(() -> archived.linkWorkItem(WorkItemReference.of("PROJ-140")))
                    .isInstanceOf(DomainConflictException.class);
            assertThatThrownBy(() -> archived.unlinkWorkItem("PROJ-123"))
                    .isInstanceOf(DomainConflictException.class);
            assertThatThrownBy(() -> archived.acceptWorkItemTitle("PROJ-123", "Anything"))
                    .isInstanceOf(DomainConflictException.class);
        }

        @Test
        void linking_leaves_the_rest_of_the_release_alone() {
            // The field was added to an aggregate every other operation already
            // copies; this catches a copyWith that forgot to carry something.
            ReleasePack before = pack();
            ReleasePack after = before.linkWorkItem(WorkItemReference.of("PROJ-123"));

            assertThat(after.id()).isEqualTo(before.id());
            assertThat(after.name()).isEqualTo(before.name());
            assertThat(after.contents()).isEqualTo(before.contents());
            assertThat(after.handover()).isEqualTo(before.handover());
            assertThat(after.iterations()).isEqualTo(before.iterations());
            assertThat(after.isArchived()).isEqualTo(before.isArchived());
        }
    }
}
