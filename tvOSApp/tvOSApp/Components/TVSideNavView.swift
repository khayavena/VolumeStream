import SwiftUI
import VolumeStreamShared

struct TVSideNavView: View {
    let items: [VolumeStreamTVViewModel.NavItem]
    let selectedRoute: String
    let navExpanded: Bool
    let focusedRoute: FocusState<String?>.Binding
    let accent: Color
    let carouselLabel: Color
    let selectedNavBg: Color
    let focusedNavBg: Color
    let sideNavBg: Color
    let sideNavExpandedBg: Color
    let navRailCollapsed: CGFloat
    let navRailExpanded: CGFloat
    let onSelect: (String) -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            ForEach(items) { item in
                let isFocused = focusedRoute.wrappedValue == item.route
                HStack(spacing: 12) {
                    Image(systemName: item.sfSymbol)
                    if navExpanded {
                        Text(item.label)
                        if selectedRoute == item.route {
                            Spacer(minLength: 8)
                            RoundedRectangle(cornerRadius: 2)
                                .fill(accent)
                                .frame(width: 3, height: 24)
                        }
                    }
                }
                .foregroundStyle(selectedRoute == item.route ? accent : (isFocused ? .white : carouselLabel))
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(.vertical, 10)
                .padding(.horizontal, 12)
                .background(
                    selectedRoute == item.route
                        ? selectedNavBg
                        : (isFocused ? focusedNavBg : Color.clear)
                )
                .clipShape(RoundedRectangle(cornerRadius: 10))
                .contentShape(RoundedRectangle(cornerRadius: 10))
                .focusable(true)
                .disableSystemFocusEffectIfAvailable()
                .focused(focusedRoute, equals: item.route)
                .onTapGesture {
                    onSelect(item.route)
                }
            }

            Spacer()
        }
        .frame(width: navExpanded ? navRailExpanded : navRailCollapsed)
        .padding(.top, 44)
        .padding(.horizontal, 8)
        .background(navExpanded ? sideNavExpandedBg : sideNavBg)
        .animation(.easeInOut(duration: 0.2), value: navExpanded)
    }
}

