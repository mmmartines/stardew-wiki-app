import SwiftUI

/// Favoritos ou Histórico (de todos os idiomas). Deslizar para o lado remove, como em qualquer lista do iOS.
struct EntriesScreen: View {
    let favorites: Bool

    @EnvironmentObject var model: AppModel
    @EnvironmentObject var store: Store
    @State private var confirmClear = false
    @State private var otherLang: Entry?

    private var items: [Entry] { favorites ? store.favorites : store.history }

    /// Histórico agrupado por dia ("Hoje", "Ontem", data); favoritos numa lista só.
    private var groups: [(String, [Entry])] {
        if favorites { return [("", items)] }
        var out: [(String, [Entry])] = []
        for e in items {
            let g = dayLabel(e.time)
            if out.last?.0 == g { out[out.count - 1].1.append(e) } else { out.append((g, [e])) }
        }
        return out
    }

    var body: some View {
        Group {
            if items.isEmpty {
                ContentUnavailableView {
                    Label(t(favorites ? "tab_favorites" : "tab_history"), systemImage: favorites ? "star" : "clock")
                } description: {
                    Text(t(favorites ? "empty_favorites" : "empty_history"))
                }
            } else {
                List {
                    ForEach(groups, id: \.0) { g in
                        Section {
                            ForEach(g.1) { e in row(e) }
                        } header: {
                            if !g.0.isEmpty { Text(g.0).font(.pixel(14, relativeTo: .subheadline)) }
                        }
                    }
                }
                .listStyle(.insetGrouped)
                .scrollContentBackground(.hidden)
            }
        }
        .background(Color.pageBackground)
        .offlineBanner()
        .navigationTitle(t(favorites ? "tab_favorites" : "tab_history"))
        .toolbar {
            ToolbarItemGroup(placement: .topBarTrailing) {
                if !favorites && !items.isEmpty {
                    Button(role: .destructive) { confirmClear = true } label: { Image(systemName: "trash") }
                        .accessibilityLabel(Text(t("clear_history")))
                }
                LanguageButton()
            }
        }
        .confirmationDialog(t("clear_history_q"), isPresented: $confirmClear, titleVisibility: .visible) {
            Button(t("delete"), role: .destructive) { store.clearHistory() }
            Button(t("cancel"), role: .cancel) {}
        }
        .alert(otherLang?.title ?? "", isPresented: Binding(get: { otherLang != nil }, set: { if !$0 { otherLang = nil } })) {
            if let e = otherLang, let from = Lang.wiki(e.lang) {
                Button(t("open_in_fmt", from.flag + " " + from.name)) {
                    model.setLang(e.lang)
                    model.push(.article(e.title))
                }
            }
            Button(t("cancel"), role: .cancel) {}
        } message: {
            if let e = otherLang, let from = Lang.wiki(e.lang) {
                Text(t("not_in_lang_fmt", Lang.current.name, from.name))
            }
        }
    }

    private func row(_ e: Entry) -> some View {
        Button {
            model.open(e) { otherLang = $0 }
        } label: {
            VStack(alignment: .leading, spacing: 2) {
                Text(e.title).foregroundStyle(Color.ink)
                let sub = subtitle(e)
                if !sub.isEmpty { Text(sub).font(.footnote).foregroundStyle(.secondary) }
            }
        }
        .swipeActions(edge: .trailing, allowsFullSwipe: true) {
            Button(role: .destructive) { remove(e) } label: { Label(t("remove"), systemImage: "trash") }
        }
        .contextMenu {
            Button(role: .destructive) { remove(e) } label: { Label(t("remove"), systemImage: "trash") }
        }
        .listRowBackground(Color.parchment)
    }

    private func subtitle(_ e: Entry) -> String {
        var parts: [String] = []
        if !favorites && e.time > 0 {
            parts.append(Date(timeIntervalSince1970: e.time).formatted(date: .omitted, time: .shortened))
        }
        if e.lang != Lang.code, let w = Lang.wiki(e.lang) { parts.append(w.flag + "  " + w.name) }
        return parts.joined(separator: "  ·  ")
    }

    private func remove(_ e: Entry) {
        store.remove(e, favorite: favorites)
        model.show(toast: t("removed_fmt", e.title))
    }

    private func dayLabel(_ time: Double) -> String {
        if time <= 0 { return t("day_earlier") }
        let d = Date(timeIntervalSince1970: time)
        let cal = Calendar.current
        if cal.isDateInToday(d) { return t("day_today") }
        if cal.isDateInYesterday(d) { return t("day_yesterday") }
        return d.formatted(.dateTime.day().month(.abbreviated).year().locale(Locale(identifier: Lang.code)))
    }
}
