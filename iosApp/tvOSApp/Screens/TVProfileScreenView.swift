import SwiftUI
import VolumeStreamShared

struct TVProfileScreenView: View {
    let accent: Color
    let isLoggedIn: Bool
    let currentUserEmail: String
    let profile: VolumeStreamTVViewModel.ProfileSummary?
    @Binding var loginEmail: String
    @Binding var loginPassword: String
    let canSubmitLogin: Bool
    let isLoginSubmitting: Bool
    let errorMessage: String
    let isLoading: Bool
    let onRefreshProfile: () -> Void
    let onLogout: () -> Void
    let onLogin: () -> Void
    let onRetry: () -> Void

    private let inputBackground = Color.black.opacity(0.72)
    private let inputBorder = Color(red: 0 / 255.0, green: 230 / 255.0, blue: 118 / 255.0, opacity: 0.42)
    private let formPanelBackground = Color.black.opacity(0.52)

    var body: some View {
        VStack(alignment: .leading, spacing: 18) {
            Text("Profile")
                .font(.system(size: 44, weight: .bold))
                .foregroundStyle(.white)

            if isLoggedIn {
                Text("Email: \(currentUserEmail)")
                    .foregroundStyle(.white)
                if let profile {
                    Text("Name: \(profile.fullName)")
                        .foregroundStyle(.white)
                    Text("Phone: \(profile.phone)")
                        .foregroundStyle(.white)
                    Text("Address: \(profile.address)")
                        .foregroundStyle(.white)
                }
                HStack(spacing: 12) {
                    Button("Refresh") {
                        onRefreshProfile()
                    }
                    .buttonStyle(TVPrimaryButtonStyle(accent: accent))
                    .frame(width: 180)

                    Button("Logout") {
                        onLogout()
                    }
                    .buttonStyle(TVPrimaryButtonStyle(accent: accent.opacity(0.82)))
                    .frame(width: 180)
                }
            } else {
                VStack(alignment: .leading, spacing: 14) {
                    Text("Welcome back")
                        .font(.system(size: 28, weight: .semibold))
                        .foregroundStyle(.white)
                    Text("Sign in to sync your profile and continue playback")
                        .font(.system(size: 16))
                        .foregroundStyle(.white.opacity(0.75))

                    TextField("Email", text: $loginEmail)
                        .textFieldStyle(.plain)
                        .foregroundStyle(.white)
                        .tint(accent)
                        .padding(.horizontal, 14)
                        .frame(width: 520, height: 48)
                        .background(inputBackground)
                        .overlay(
                            RoundedRectangle(cornerRadius: 10)
                                .stroke(inputBorder, lineWidth: 1.2)
                        )
                        .clipShape(RoundedRectangle(cornerRadius: 10))

                    SecureField("Password", text: $loginPassword)
                        .textFieldStyle(.plain)
                        .foregroundStyle(.white)
                        .tint(accent)
                        .padding(.horizontal, 14)
                        .frame(width: 520, height: 48)
                        .background(inputBackground)
                        .overlay(
                            RoundedRectangle(cornerRadius: 10)
                                .stroke(inputBorder, lineWidth: 1.2)
                        )
                        .clipShape(RoundedRectangle(cornerRadius: 10))
                        .onSubmit {
                            onLogin()
                        }

                    Button("Login") {
                        onLogin()
                    }
                    .buttonStyle(TVPrimaryButtonStyle(accent: accent))
                    .frame(width: 200)
                    .disabled(!canSubmitLogin)

                    if isLoginSubmitting {
                        ProgressView("Signing in...")
                            .tint(accent)
                    }
                }
                .padding(18)
                .background(formPanelBackground)
                .overlay(
                    RoundedRectangle(cornerRadius: 14)
                        .stroke(inputBorder, lineWidth: 1.0)
                )
                .clipShape(RoundedRectangle(cornerRadius: 14))
            }

            if !errorMessage.isEmpty {
                TVRouteErrorView(text: errorMessage, isLoading: isLoading, onRetry: onRetry)
            }

            Spacer()
        }
    }
}

