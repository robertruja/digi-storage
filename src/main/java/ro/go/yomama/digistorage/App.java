package ro.go.yomama.digistorage;

import org.crumbs.core.annotation.CrumbsApplication;
import org.crumbs.core.context.CrumbsApp;
import org.crumbs.core.context.CrumbsContext;

@CrumbsApplication
public class App {
    public static void main(String[] args) {
        CrumbsContext ctx = CrumbsApp.run(App.class);
        Upload up = ctx.getCrumb(Upload.class);
        up.run();
    }
}
