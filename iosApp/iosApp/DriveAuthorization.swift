import AuthenticationServices
import CryptoKit
import Security
import UIKit
import ComposeApp

/// Only the refresh token persists; access tokens are obtained on demand.
final class DriveAuthorization: NSObject, ASWebAuthenticationPresentationContextProviding {
    static let shared = DriveAuthorization()
    private let service = "com.ziaee.zabankhan.drive"
    private var session: ASWebAuthenticationSession?
    private var clientId: String {
        Bundle.main.object(forInfoDictionaryKey: "ZabankhanGoogleClientId") as? String ?? ""
    }
    private var key: [String: Any] {
        [kSecClass as String: kSecClassGenericPassword, kSecAttrService as String: service,
         kSecAttrAccount as String: "refresh-token"]
    }
    private func refreshToken() -> String? {
        var query = key
        query[kSecReturnData as String] = true
        var result: CFTypeRef?
        guard SecItemCopyMatching(query as CFDictionary, &result) == errSecSuccess,
              let data = result as? Data else { return nil }
        return String(data: data, encoding: .utf8)
    }
    private func save(_ token: String) throws {
        SecItemDelete(key as CFDictionary)
        var item = key
        item[kSecValueData as String] = Data(token.utf8)
        item[kSecAttrAccessible as String] = kSecAttrAccessibleWhenUnlockedThisDeviceOnly
        guard SecItemAdd(item as CFDictionary, nil) == errSecSuccess else {
            throw NSError(domain: "DriveKeychain", code: 1)
        }
    }
    func install() {
        let bridge = IosDriveBridge.shared
        bridge.configured = !clientId.isEmpty && !clientId.contains("$(")
        bridge.connected = self.refreshToken() != nil
        bridge.authorize = { completion in
            self.authorize { token, error in
                if token != nil { bridge.connected = true }
                _ = completion(token, error)
            }

        }
        bridge.disconnect = { completion in
            // Revoke before deleting the local credential; preserve it if revocation fails.
            guard let token = self.refreshToken() else { bridge.connected = false; _ = completion(nil); return }
            self.post("https://oauth2.googleapis.com/revoke", ["token": token]) { result in
                switch result {
                case .success:
                    SecItemDelete(self.key as CFDictionary)
                    bridge.connected = false
                    _ = completion(nil)
                case .failure: _ = completion("Could not disconnect Google Drive.")
                }
            }

        }
    }
    private func random() -> String {
        var bytes = [UInt8](repeating: 0, count: 32)
        precondition(SecRandomCopyBytes(kSecRandomDefault, bytes.count, &bytes) == errSecSuccess)
        return base64(Data(bytes))
    }
    private func base64(_ data: Data) -> String {
        data.base64EncodedString().replacingOccurrences(of: "+", with: "-")
            .replacingOccurrences(of: "/", with: "_").replacingOccurrences(of: "=", with: "")
    }
    private func authorize(_ completion: @escaping (String?, String?) -> Void) {
        guard !clientId.isEmpty else { completion(nil, nil); return }
        if let refresh = refreshToken() {
            exchange(["client_id": clientId, "grant_type": "refresh_token", "refresh_token": refresh], completion)
            return
        }
        guard session == nil else { completion(nil, "Authorization is already open."); return }
        let verifier = random(), state = random()
        let scheme = clientId.split(separator: ".").reversed().joined(separator: ".")
        let redirect = scheme + ":/oauth2redirect"
        var url = URLComponents(string: "https://accounts.google.com/o/oauth2/v2/auth")!
        url.queryItems = ["client_id": clientId, "redirect_uri": redirect, "response_type": "code",
            "scope": "https://www.googleapis.com/auth/drive.appdata", "state": state,
            "code_challenge": base64(Data(SHA256.hash(data: Data(verifier.utf8)))),
            "code_challenge_method": "S256", "access_type": "offline", "prompt": "consent"]
            .map { URLQueryItem(name: $0.key, value: $0.value) }
        session = ASWebAuthenticationSession(url: url.url!, callbackURLScheme: scheme) { callback, error in
            DispatchQueue.main.async {
                self.session = nil
                if let authError = error as? ASWebAuthenticationSessionError, authError.code == .canceledLogin {
                    completion(nil, nil); return
                }
                guard let callback = callback, callback.scheme == scheme, callback.path == "/oauth2redirect",
                      let items = URLComponents(url: callback, resolvingAgainstBaseURL: false)?.queryItems,
                      items.first(where: { $0.name == "state" })?.value == state,
                      let code = items.first(where: { $0.name == "code" })?.value else {
                    completion(nil, "Google authorization failed."); return
                }
                self.exchange(["client_id": self.clientId, "code": code, "code_verifier": verifier,
                    "redirect_uri": redirect, "grant_type": "authorization_code"], completion)
            }
        }
        session?.presentationContextProvider = self
        session?.prefersEphemeralWebBrowserSession = true
        if session?.start() != true { session = nil; completion(nil, "Could not open Google authorization.") }
    }
    private func exchange(_ fields: [String: String], _ completion: @escaping (String?, String?) -> Void) {
        post("https://oauth2.googleapis.com/token", fields) { result in
            do {
                let data = try result.get()
                let json = try JSONSerialization.jsonObject(with: data) as? [String: Any]
                guard let token = json?["access_token"] as? String else { throw NSError(domain: "DriveOAuth", code: 1) }
                if let refresh = json?["refresh_token"] as? String { try self.save(refresh) }
                completion(token, nil)
            } catch { completion(nil, "Google Drive authorization expired or failed. Disconnect and reconnect.") }
        }
    }
    private func post(_ address: String, _ fields: [String: String], _ completion: @escaping (Result<Data, Error>) -> Void) {
        var request = URLRequest(url: URL(string: address)!)
        request.httpMethod = "POST"
        request.setValue("application/x-www-form-urlencoded", forHTTPHeaderField: "Content-Type")
        let allowed = CharacterSet.alphanumerics.union(CharacterSet(charactersIn: "-._~"))
        request.httpBody = fields.map { $0.key + "=" + $0.value.addingPercentEncoding(withAllowedCharacters: allowed)! }
            .joined(separator: "&").data(using: .utf8)
        URLSession.shared.dataTask(with: request) { data, response, error in
            DispatchQueue.main.async {
                if let error = error { completion(.failure(error)) }
                else if let response = response as? HTTPURLResponse, ((200..<300).contains(response.statusCode) || (address.hasSuffix("/revoke") && response.statusCode == 400)) {
                    completion(.success(data ?? Data()))
                } else { completion(.failure(NSError(domain: "DriveOAuth", code: 2))) }
            }
        }.resume()
    }
    func presentationAnchor(for session: ASWebAuthenticationSession) -> ASPresentationAnchor {
        UIApplication.shared.connectedScenes.compactMap { $0 as? UIWindowScene }
            .flatMap { $0.windows }.first { $0.isKeyWindow } ?? ASPresentationAnchor()
    }
}
