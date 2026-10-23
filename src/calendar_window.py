"""Kalender-Widget zur Terminplanung der Praxisaufträge."""
from __future__ import annotations

from PySide6.QtCore import QDate
from PySide6.QtWidgets import (
    QCalendarWidget,
    QHBoxLayout,
    QLabel,
    QListWidget,
    QPushButton,
    QVBoxLayout,
    QWidget,
)

from pdf_parser import Praxisauftrag


class CalendarPanel(QWidget):
    """Kalender links, Liste der geplanten Praxisaufträge für den gewählten Tag rechts."""

    def __init__(self, parent: QWidget | None = None) -> None:
        super().__init__(parent)
        self.scheduled: dict[str, list[Praxisauftrag]] = {}

        self.calendar = QCalendarWidget()
        self.calendar.selectionChanged.connect(self._refresh_day_list)

        self.day_list = QListWidget()
        self.assign_button = QPushButton("Ausgewählten Praxisauftrag hier einplanen")

        left = QVBoxLayout()
        left.addWidget(QLabel("Terminkalender"))
        left.addWidget(self.calendar)

        right = QVBoxLayout()
        right.addWidget(QLabel("Eingeplant an diesem Tag"))
        right.addWidget(self.day_list)
        right.addWidget(self.assign_button)

        layout = QHBoxLayout(self)
        layout.addLayout(left)
        layout.addLayout(right)

    def _date_key(self, date: QDate | None = None) -> str:
        date = date or self.calendar.selectedDate()
        return date.toString("yyyy-MM-dd")

    def schedule(self, auftrag: Praxisauftrag, date: QDate | None = None) -> None:
        key = self._date_key(date)
        self.scheduled.setdefault(key, []).append(auftrag)
        self._refresh_day_list()

    def _refresh_day_list(self) -> None:
        self.day_list.clear()
        for auftrag in self.scheduled.get(self._date_key(), []):
            self.day_list.addItem(auftrag.titel)
