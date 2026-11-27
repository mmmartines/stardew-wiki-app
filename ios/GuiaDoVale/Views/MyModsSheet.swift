import SwiftUI

/// "Meus mods": marcar os mods que a pessoa joga. Só eles aparecem na tela inicial e na busca.
struct MyModsSheet: View {
    @EnvironmentObject var model: AppModel
    @Environment(\.dismiss) private var dismiss
    @State private var selected = Set(Sources.myMods.map(\.key))

    var body: some View {
        NavigationStack {
            List {
                Section {
                    ForEach(Sources.mods, id: \.key) { m in
                        Toggle(isOn: Binding(get: { selected.contains(m.key) },
                                             set: { on in if on { selected.insert(m.key) } else { selected.remove(m.key) } })) {
                            Text(m.emoji + "  " + m.name).foregroundStyle(Color.ink)
                        }
                        .tint(Color.accentColor)
                        .listRowBackground(Color.parchment)
                    }
                } footer: {
                    Text(t("my_mods_hint"))
                }
            }
            .listStyle(.insetGrouped)
            .scrollContentBackground(.hidden)
            .background(Color.pageBackground)
            .navigationTitle(t("my_mods"))
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button(t("cancel")) { close(save: false) }
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button(t("ok")) { close(save: true) }.bold()
                }
            }
        }
        .presentationDetents([.medium, .large])
        .interactiveDismissDisabled(!Sources.myModsAsked)
    }

    private func close(save: Bool) {
        // na primeira vez, "Cancelar" mantém todos os mods, mas não pergunta de novo
        if save || !Sources.myModsAsked {
            Sources.setMyMods(save ? selected : Set(Sources.myMods.map(\.key)))
            if !Sources.isMod(Sources.startChoice) && Sources.isMod(model.homeChoice) && !selected.contains(model.homeChoice) {
                model.homeChoice = Sources.baseChoice
            }
            model.myModsVersion += 1
        }
        dismiss()
    }
}
