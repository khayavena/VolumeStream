import SwiftUI

struct TVStatusPillView: View {
    let text: String
    let isError: Bool

    var body: some View {
        VStack {
            HStack {
                Spacer()
                Text(text)
                    .padding(.vertical, 8)
                    .padding(.horizontal, 12)
                    .background((isError ? Color.red : Color.black).opacity(0.8))
                    .clipShape(RoundedRectangle(cornerRadius: 8))
                    .foregroundStyle(.white)
            }
            Spacer()
        }
        .padding(24)
    }
}

struct TVRouteErrorView: View {
    let text: String
    let isLoading: Bool
    let onRetry: () -> Void

    var body: some View {
        HStack(spacing: 12) {
            Text(text)
                .font(.system(size: 18))
                .foregroundStyle(.red)
            Button("Retry") {
                onRetry()
            }
            .buttonStyle(.bordered)
            .disabled(isLoading)
        }
    }
}

struct TVLoadingIndicatorView: View {
    let background: Color

    var body: some View {
        VStack {
            HStack {
                Spacer()
                ProgressView()
                    .padding(10)
                    .background(background)
                    .clipShape(RoundedRectangle(cornerRadius: 10))
            }
            Spacer()
        }
        .padding(24)
    }
}

