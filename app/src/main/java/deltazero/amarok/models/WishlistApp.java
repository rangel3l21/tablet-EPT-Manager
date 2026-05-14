package deltazero.amarok.models;

public class WishlistApp {
    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_FETCHING_URLS = "FETCHING_URLS";
    public static final String STATUS_DOWNLOADING = "DOWNLOADING";
    public static final String STATUS_INSTALLING = "INSTALLING";
    public static final String STATUS_SUCCESS = "SUCCESS";
    public static final String STATUS_ERROR = "ERROR";

    public String packageName;
    public String status;
    public String errorMessage;
    public boolean selected; // checkbox — marcado por padrão

    public WishlistApp(String packageName) {
        this.packageName = packageName;
        this.status = STATUS_PENDING;
        this.errorMessage = "";
        this.selected = true;
    }
}
