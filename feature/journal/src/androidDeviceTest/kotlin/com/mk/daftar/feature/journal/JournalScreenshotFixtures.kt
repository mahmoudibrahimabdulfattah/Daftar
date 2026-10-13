package com.mk.daftar.feature.journal

import androidx.compose.runtime.Composable
import com.mk.daftar.core.domain.storage.StorageError
import com.mk.daftar.core.model.Decimal
import com.mk.daftar.core.model.NoteId
import com.mk.daftar.core.model.ZakatPaymentId
import com.mk.daftar.core.navigation.ZakatAddRoute
import com.mk.daftar.feature.journal.notes.NoteConfirmation
import com.mk.daftar.feature.journal.notes.NoteDraft
import com.mk.daftar.feature.journal.notes.NoteEditorUiState
import com.mk.daftar.feature.journal.notes.NoteRow
import com.mk.daftar.feature.journal.notes.NoteValues
import com.mk.daftar.feature.journal.notes.NotesListUiState
import com.mk.daftar.feature.journal.ui.JournalContent
import com.mk.daftar.feature.journal.ui.NoteEditorContent
import com.mk.daftar.feature.journal.ui.NotesListContent
import com.mk.daftar.feature.journal.ui.ZakatEditorContent
import com.mk.daftar.feature.journal.ui.ZakatListContent
import com.mk.daftar.feature.journal.zakat.ZakatConfirmation
import com.mk.daftar.feature.journal.zakat.ZakatDraft
import com.mk.daftar.feature.journal.zakat.ZakatEditorUiState
import com.mk.daftar.feature.journal.zakat.ZakatListUiState
import com.mk.daftar.feature.journal.zakat.ZakatPaymentRow
import com.mk.daftar.feature.journal.zakat.ZakatValues
import com.mk.daftar.feature.journal.zakat.ZakatYear
import kotlinx.datetime.LocalDate

private val SHOT_TODAY = LocalDate(2026, 10, 12)

private fun row(id: String, date: LocalDate, amount: String, note: String?): ZakatPaymentRow =
    ZakatPaymentRow(ZakatPaymentId(id), date, Decimal.parseCanonical(amount), note)

private val populatedYears = listOf(
    ZakatYear(
        year = 2026,
        total = Decimal.parseCanonical("15000"),
        payments = listOf(
            row("a", LocalDate(2026, 3, 20), "10000", "دفعة رمضان"),
            row("b", LocalDate(2026, 1, 15), "5000", "دفعة مقدّمة"),
        ),
    ),
)

private fun note(id: String, title: String, body: String, day: LocalDate) = NoteRow(NoteId(id), title, body, day)

private val populatedNotes = listOf(
    note(
        "n1",
        "سلفة لأحمد",
        "أقرضته 5,000 ج.م في أول أكتوبر.\nنتفق على السداد آخر الشهر.",
        LocalDate(2026, 10, 1),
    ),
    note(
        "n2",
        "فاتورة السبايك",
        "الفاتورة الورقية مع أوراق البيت.\nالوزن المسجّل هو الوزن الصافي.",
        LocalDate(2026, 9, 28),
    ),
)

private fun zakatEditor(
    isNew: Boolean,
    draft: ZakatDraft,
    baseline: ZakatValues? = null,
    attempted: Boolean = false,
    conflict: Boolean = false,
    failure: JournalFailure? = null,
    confirmation: ZakatConfirmation? = null,
    concealed: Boolean = false,
): ZakatEditorUiState = ZakatEditorUiState(
    phase = JournalPhase.Ready,
    isNew = isNew,
    draft = draft,
    baseline = baseline ?: ZakatValues(draft.dateText, draft.amountText, draft.noteText.ifEmpty { null }),
    today = SHOT_TODAY,
    attempted = attempted,
    conflict = conflict,
    failure = failure,
    confirmation = confirmation,
    concealed = concealed,
)

private fun noteEditor(
    isNew: Boolean,
    draft: NoteDraft,
    attempted: Boolean = false,
    confirmation: NoteConfirmation? = null,
    concealed: Boolean = false,
): NoteEditorUiState = NoteEditorUiState(
    phase = JournalPhase.Ready,
    isNew = isNew,
    draft = draft,
    baseline = NoteValues(draft.title.trim(), draft.body),
    attempted = attempted,
    confirmation = confirmation,
    concealed = concealed,
)

/** Every captured frame, in the order the files are written. Each is drawn with the theme the test asks for. */
@Composable
internal fun JournalFrameContent(name: String) {
    when (name) {
        "overview_populated" -> JournalContent(
            state = JournalUiState(JournalPhase.Ready, LocalDate(2026, 3, 20), 2, hidden = false),
            onIntent = {},
        )

        "overview_empty" -> JournalContent(state = JournalUiState(JournalPhase.Ready, null, 0), onIntent = {})

        "overview_loading" -> JournalContent(state = JournalUiState(JournalPhase.Loading), onIntent = {})

        "zakat_populated" -> ZakatListContent(
            state = ZakatListUiState(JournalPhase.Ready, populatedYears),
            onIntent = {},
            onBack = {},
        )

        "zakat_empty" -> ZakatListContent(state = ZakatListUiState(JournalPhase.Empty), onIntent = {}, onBack = {})

        "zakat_masked" -> ZakatListContent(
            state = ZakatListUiState(JournalPhase.Ready, populatedYears, hidden = true),
            onIntent = {},
            onBack = {},
        )

        "zakat_reveal" -> ZakatListContent(
            state = ZakatListUiState(JournalPhase.Ready, populatedYears, hidden = true, pendingRoute = ZakatAddRoute()),
            onIntent = {},
            onBack = {},
        )

        "zakat_add_empty" -> ZakatEditorContent(
            state = zakatEditor(true, ZakatDraft(dateText = "2026-10-12")),
            onIntent = {},
            onBack = {},
        )

        "zakat_add_form" -> ZakatEditorContent(
            state = zakatEditor(true, ZakatDraft("2026-03-20", "10000", "دفعة رمضان")),
            onIntent = {},
            onBack = {},
        )

        "zakat_add_error" -> ZakatEditorContent(
            state = zakatEditor(
                true,
                ZakatDraft("2026-10-13", "1.", ""),
                attempted = true,
            ),
            onIntent = {},
            onBack = {},
        )

        "zakat_edit_form" -> ZakatEditorContent(
            state = zakatEditor(false, ZakatDraft("2026-03-20", "10000", "دفعة رمضان")),
            onIntent = {},
            onBack = {},
        )

        "zakat_conflict" -> ZakatEditorContent(
            state = zakatEditor(false, ZakatDraft("2026-03-20", "12000", "دفعة رمضان"), conflict = true),
            onIntent = {},
            onBack = {},
        )

        "zakat_save_failed" -> ZakatEditorContent(
            state = zakatEditor(
                true,
                ZakatDraft("2026-03-20", "10000", ""),
                failure = JournalFailure(JournalAction.Save, StorageError.DiskFull),
            ),
            onIntent = {},
            onBack = {},
        )

        "zakat_delete_confirm" -> ZakatEditorContent(
            state = zakatEditor(
                false,
                ZakatDraft("2026-03-20", "10000", "دفعة رمضان"),
                confirmation = ZakatConfirmation.Delete,
            ),
            onIntent = {},
            onBack = {},
        )

        "zakat_discard_confirm" -> ZakatEditorContent(
            state = zakatEditor(
                true,
                ZakatDraft("2026-03-20", "10000", ""),
                confirmation = ZakatConfirmation.Discard,
            ),
            onIntent = {},
            onBack = {},
        )

        "zakat_concealed" -> ZakatEditorContent(
            state = zakatEditor(true, ZakatDraft("2026-03-20", "10000", ""), concealed = true),
            onIntent = {},
            onBack = {},
        )

        "notes_populated" -> NotesListContent(
            state = NotesListUiState(JournalPhase.Ready, populatedNotes),
            onIntent = {},
            onBack = {},
        )

        "notes_empty" -> NotesListContent(state = NotesListUiState(JournalPhase.Empty), onIntent = {}, onBack = {})

        "notes_masked" -> NotesListContent(
            state = NotesListUiState(JournalPhase.Ready, populatedNotes, hidden = true),
            onIntent = {},
            onBack = {},
        )

        "note_add_form" -> NoteEditorContent(
            state = noteEditor(true, NoteDraft("سلفة لأحمد", "أقرضته 5,000 ج.م\nنتفق على السداد")),
            onIntent = {},
            onBack = {},
        )

        "note_add_error" -> NoteEditorContent(
            state = noteEditor(true, NoteDraft("", "   "), attempted = true),
            onIntent = {},
            onBack = {},
        )

        "note_edit_delete_confirm" -> NoteEditorContent(
            state = noteEditor(
                false,
                NoteDraft("سلفة لأحمد", "أقرضته 5,000 ج.م في أول أكتوبر."),
                confirmation = NoteConfirmation.Delete,
            ),
            onIntent = {},
            onBack = {},
        )

        else -> error("unknown frame $name")
    }
}

/** The frame names, in the order the screenshot test captures them. */
internal val journalFrameNames: List<String> = listOf(
    "overview_populated",
    "overview_empty",
    "overview_loading",
    "zakat_populated",
    "zakat_empty",
    "zakat_masked",
    "zakat_reveal",
    "zakat_add_empty",
    "zakat_add_form",
    "zakat_add_error",
    "zakat_edit_form",
    "zakat_conflict",
    "zakat_save_failed",
    "zakat_delete_confirm",
    "zakat_discard_confirm",
    "zakat_concealed",
    "notes_populated",
    "notes_empty",
    "notes_masked",
    "note_add_form",
    "note_add_error",
    "note_edit_delete_confirm",
)
