import SwiftUI
import UIKit

struct ArtworkImageView: View {
    let urlString: String
    let placeholderColor: Color

    private let placeholderIconColor = Color(red: 0 / 255.0, green: 230 / 255.0, blue: 118 / 255.0, opacity: 0.78)

    @StateObject private var loader = LocalTLSArtworkLoader()

    var body: some View {
        ZStack {
            // Keep a stable dark base so transparent artwork never reveals white.
            Rectangle().fill(placeholderColor)

            if let image = loader.image {
                Image(uiImage: image)
                    .resizable()
                    // Fill card width/height for a denser, TV-style visual treatment.
                    .aspectRatio(image.size, contentMode: .fill)
                    .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .center)
                    .clipped()
            } else {
                Image(systemName: "film")
                    .foregroundStyle(placeholderIconColor)
            }
        }
        .background(placeholderColor)
        .clipped()
        .onAppear {
            loader.load(urlString: urlString)
        }
        .onChange(of: urlString) { value in
            loader.load(urlString: value)
        }
    }
}

@MainActor
private final class LocalTLSArtworkLoader: NSObject, ObservableObject, URLSessionDelegate {
    @Published var image: UIImage?

    private var currentURL: URL?
    private var task: URLSessionDataTask?
    private lazy var session = URLSession(
        configuration: .ephemeral,
        delegate: self,
        delegateQueue: nil
    )

    func load(urlString: String) {
        let trimmed = urlString.trimmingCharacters(in: .whitespacesAndNewlines)
        guard let url = URL(string: trimmed), !trimmed.isEmpty else {
            image = nil
            currentURL = nil
            task?.cancel()
            task = nil
            return
        }
        guard currentURL != url else { return }

        currentURL = url
        image = nil
        task?.cancel()
        task = session.dataTask(with: url) { [weak self] data, _, _ in
            guard let self else { return }
            guard let data, let decoded = UIImage(data: data) else { return }
            Task { @MainActor in
                if self.currentURL == url {
                    self.image = decoded
                }
            }
        }
        task?.resume()
    }

    nonisolated func urlSession(
        _ session: URLSession,
        didReceive challenge: URLAuthenticationChallenge,
        completionHandler: @escaping (URLSession.AuthChallengeDisposition, URLCredential?) -> Void
    ) {
        guard challenge.protectionSpace.authenticationMethod == NSURLAuthenticationMethodServerTrust,
              let trust = challenge.protectionSpace.serverTrust else {
            completionHandler(.performDefaultHandling, nil)
            return
        }

        let host = challenge.protectionSpace.host.lowercased()
        if host == "localhost" || host == "127.0.0.1" || host == "::1" {
            completionHandler(.useCredential, URLCredential(trust: trust))
        } else {
            completionHandler(.performDefaultHandling, nil)
        }
    }
}

