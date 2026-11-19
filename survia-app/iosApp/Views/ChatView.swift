import SwiftUI
import CoreLocation

struct ChatMessageIOS: Identifiable {
    let id = UUID()
    let isUser: BooleanLiteralType
    let text: String
    let isWarning: Bool
}

struct ChatView: View {
    @State private var inputText: String = ""
    @State private var messages: [ChatMessageIOS] = [
        ChatMessageIOS(
            isUser: false,
            text: "SurvIA (iOS) initialisé 100% hors-ligne. Modèle Gemma 4 E4B IT mappé en mémoire via Metal GPU. 3 outils natifs actifs.",
            isWarning: false
        )
    ]

    var body: some View {
        VStack(spacing: 0) {
            // Header
            HStack {
                VStack(alignment: .leading, spacing: 2) {
                    Text("SURVIA • AGENT HORS-LIGNE")
                        .font(.headline)
                        .fontWeight(.bold)
                        .foregroundColor(.white)
                    Text("● 0% Réseau • Gemma 4 E4B (Metal GPU / mmap)")
                        .font(.caption2)
                        .foregroundColor(Color(red: 0.06, green: 0.73, blue: 0.51))
                }
                Spacer()
                Button(action: openOrganicMaps) {
                    Text("Carte (om://)")
                        .font(.caption)
                        .padding(.horizontal, 10)
                        .padding(.vertical, 6)
                        .background(Color(red: 0.12, green: 0.18, blue: 0.24))
                        .foregroundColor(Color(red: 0.22, green: 0.74, blue: 0.97))
                        .cornerRadius(6)
                }
            }
            .padding()
            .background(Color(red: 0.07, green: 0.09, blue: 0.13))

            // Message List
            ScrollView {
                LazyVStack(spacing: 10) {
                    ForEach(messages) { msg in
                        VStack(alignment: .leading) {
                            Text(msg.text)
                                .font(msg.isUser ? .body : .system(.caption, design: .monospaced))
                                .foregroundColor(msg.isWarning ? Color(red: 1.0, green: 0.8, blue: 0.8) : .white)
                                .padding(12)
                        }
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .background(msg.isWarning ? Color(red: 0.5, green: 0.1, blue: 0.1) : (msg.isUser ? Color(red: 0.12, green: 0.18, blue: 0.24) : Color(red: 0.07, green: 0.09, blue: 0.13)))
                        .cornerRadius(8)
                        .overlay(
                            RoundedRectangle(cornerRadius: 8)
                                .stroke(msg.isWarning ? Color.red : Color.gray.opacity(0.3), lineWidth: 1)
                        )
                    }
                }
                .padding(12)
            }
            .background(Color(red: 0.04, green: 0.05, blue: 0.07))

            // Bottom Input
            HStack(spacing: 8) {
                TextField("Pose une question ou décris une urgence...", text: $inputText)
                    .padding(10)
                    .background(Color(red: 0.04, green: 0.05, blue: 0.07))
                    .cornerRadius(8)
                    .foregroundColor(.white)

                Button(action: sendMessage) {
                    Text("Envoyer")
                        .fontWeight(.bold)
                        .foregroundColor(Color(red: 0.04, green: 0.05, blue: 0.07))
                        .padding(.horizontal, 14)
                        .padding(.vertical, 10)
                        .background(Color(red: 0.06, green: 0.73, blue: 0.51))
                        .cornerRadius(8)
                }
            }
            .padding(10)
            .background(Color(red: 0.07, green: 0.09, blue: 0.13))
        }
    }

    private func openOrganicMaps() {
        if let url = URL(string: "om://map?ll=45.1885,5.7245&n=Point_Survie") {
            UIApplication.shared.open(url)
        }
    }

    private func sendMessage() {
        guard !inputText.trimmingCharacters(in: .whitespaces).isEmpty else { return }
        let q = inputText
        messages.append(ChatMessageIOS(isUser: true, text: q, isWarning: false))
        inputText = ""

        let lower = q.lowercased()
        let isEdibility = lower.contains("champignon") || lower.contains("plante") || lower.contains("comestible")

        if isEdibility {
            messages.append(ChatMessageIOS(
                isUser: false,
                text: "⚠️ GARDE-FOU VITAL ACTIVÉ :\nInterdiction formelle de consommer ce spécimen sur photo ou description. Risque mortel (sosies Amanita phalloides, Ciguë). Principe de précaution absolu.",
                isWarning: true
            ))
        } else {
            messages.append(ChatMessageIOS(
                isUser: false,
                text: "Simulation locale Gemma 4 E4B IT :\nProtocole de survie validé. Filtrer l'eau par ébullition 1 minute franche. Isoler l'abri à 30 cm du sol.",
                isWarning: false
            ))
        }
    }
}
