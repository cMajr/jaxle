package jaxle;

import java.util.List;
import java.util.stream.Collectors;

final class Banner {
    private static final String LOGO = """
             ██╗ █████╗ ██╗  ██╗██╗     ███████╗
             ██║██╔══██╗╚██╗██╔╝██║     ██╔════╝
             ██║███████║ ╚███╔╝ ██║     █████╗
        ██   ██║██╔══██║ ██╔██╗ ██║     ██╔══╝
        ╚█████╔╝██║  ██║██╔╝ ██╗███████╗███████╗
         ╚════╝ ╚═╝  ╚═╝╚═╝  ╚═╝╚══════╝╚══════╝
        """;

    private Banner() {

    }

    static String render(String version, String javaVersion, List<Router.Route> routes, int port, long elapsedMs) {
        var banner = new StringBuilder()
            .append('\n')
            .append(LOGO.indent(2))
            .append('\n')
            .append("  v%s | zero dependencies | Java %s\n".formatted(version, javaVersion))
            .append('\n');

        if (!routes.isEmpty()) {
            banner.append(routeTable(routes)).append('\n');
        }

        return banner
            .append("  Listening on http://localhost:%d (started in %d ms)\n".formatted(port, elapsedMs))
            .append('\n')
            .toString();
    }

    private static String routeTable(List<Router.Route> routes) {
        int width = routes.stream()
            .mapToInt(route -> route.method().name().length())
            .max()
            .orElse(0);

        String format = "  %-" + width + "s  %s\n";

        return routes.stream()
            .map(route -> format.formatted(route.method(), route.path()))
            .collect(Collectors.joining());
    }
}
