import SwiftUI

@main
struct GuiaDoValeApp: App {
    @StateObject private var model = AppModel()
    @StateObject private var store = Store.shared
    @StateObject private var net = Net.shared

    var body: some Scene {
        WindowGroup {
            RootView()
                .environmentObject(model)
                .environmentObject(store)
                .environmentObject(net)
                // datas, botões do sistema e direção do texto seguem o idioma da wiki
                .environment(\.locale, Locale(identifier: model.lang))
                .id(model.lang) // troca de idioma redesenha tudo com os novos textos
        }
    }
}
