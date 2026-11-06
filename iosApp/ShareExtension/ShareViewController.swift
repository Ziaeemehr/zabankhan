import UIKit
import UniformTypeIdentifiers

final class ShareViewController: UIViewController {
    private let message = UILabel()
    private let done = UIButton(type: .system)

    override func viewDidLoad() {
        super.viewDidLoad()
        view.backgroundColor = .systemBackground
        message.numberOfLines = 0
        message.textAlignment = .natural
        message.font = .preferredFont(forTextStyle: .body)
        message.adjustsFontForContentSizeCategory = true
        done.setTitle(NSLocalizedString("done", comment: "Finish sharing"), for: .normal)
        done.addTarget(self, action: #selector(finish), for: .touchUpInside)
        done.isEnabled = false
        let stack = UIStackView(arrangedSubviews: [message, done])
        stack.axis = .vertical
        stack.spacing = 24
        stack.translatesAutoresizingMaskIntoConstraints = false
        view.addSubview(stack)
        NSLayoutConstraint.activate([
            stack.leadingAnchor.constraint(equalTo: view.safeAreaLayoutGuide.leadingAnchor, constant: 24),
            stack.trailingAnchor.constraint(equalTo: view.safeAreaLayoutGuide.trailingAnchor, constant: -24),
            stack.centerYAnchor.constraint(equalTo: view.safeAreaLayoutGuide.centerYAnchor)
        ])
        let items = extensionContext?.inputItems as? [NSExtensionItem] ?? []
        let candidates = items.flatMap { item in
            (item.attachments ?? []).map { ($0, item.attributedContentText?.string) }
        }
        loadFirst(candidates[...])
    }

    private func loadFirst(_ candidates: ArraySlice<(NSItemProvider, String?)>) {
        guard let (provider, title) = candidates.first else {
            showResult(saved: false)
            return
        }
        // Prefer the URL representation when a provider offers both URL and text.
        let type = provider.hasItemConformingToTypeIdentifier(UTType.url.identifier)
            ? UTType.url.identifier : UTType.plainText.identifier
        guard provider.hasItemConformingToTypeIdentifier(type) else {
            loadFirst(candidates.dropFirst())
            return
        }
        provider.loadItem(forTypeIdentifier: type, options: nil) { [weak self] value, error in
            DispatchQueue.main.async {
                guard let self = self else { return }
                var text: String?
                if error == nil {
                    if let url = value as? URL {
                        text = url.absoluteString
                    } else if let string = value as? String {
                        text = string
                    } else if let attributed = value as? NSAttributedString {
                        text = attributed.string
                    } else if let data = value as? Data {
                        text = String(data: data, encoding: .utf8)
                    }
                }
                guard var payload = text, !payload.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else {
                    self.loadFirst(candidates.dropFirst())
                    return
                }
                if type == UTType.url.identifier, let title = title,
                   !title.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty,
                   title != payload {
                    payload += "\n" + title
                }
                guard let defaults = UserDefaults(suiteName: "group.com.ziaee.zabankhan") else {
                    self.showResult(saved: false)
                    return
                }
                // Several shares before the app opens are kept together instead of overwriting each other.
                if let earlier = defaults.string(forKey: "pendingShare"), !earlier.isEmpty {
                    payload = earlier + "\n\n" + payload
                }
                defaults.set(String(payload.prefix(100_000)), forKey: "pendingShare")
                self.showResult(saved: true)
            }
        }
    }

    private func showResult(saved: Bool) {
        message.text = NSLocalizedString(saved ? "sent" : "error", comment: "Share result")
        done.isEnabled = true
    }

    @objc private func finish() {
        extensionContext?.completeRequest(returningItems: nil, completionHandler: nil)
    }
}
