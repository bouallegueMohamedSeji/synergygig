package tn.esprit.synergygig.controllers;

import javafx.animation.TranslateTransition;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

public class SidebarController {

    @FXML private VBox sidebarRoot;
    
    // Header Elements
    @FXML private VBox hamburgerBtn;
    @FXML private javafx.scene.layout.Pane line1;
    @FXML private javafx.scene.layout.Pane line2;
    @FXML private javafx.scene.layout.Pane line3;
    
    @FXML private VBox profileContainer;
    @FXML private Label lblBrand;
    @FXML private Label lblRole;
    
    @FXML private Label lblPlatform;
    @FXML private Label lblManagement;
    
    @FXML private Button btnDashboard;
    @FXML private Button btnOffers;
    @FXML private Button btnGigs;
    @FXML private Button btnCommunity;
    @FXML private Button btnApplications;
    @FXML private Button btnHR;
    @FXML private Button btnLogout;

    private boolean isCollapsed = false;
    private final double EXPANDED_WIDTH = 250;
    private final double COLLAPSED_WIDTH = 80; // Slightly wider for icon-only mode

    private MainLayoutController mainLayoutController;

    public void setMainLayoutController(MainLayoutController controller) {
        this.mainLayoutController = controller;
    }

    @FXML
    public void initialize() {
        // Init state
        sidebarRoot.setPrefWidth(EXPANDED_WIDTH);
        sidebarRoot.setMinWidth(COLLAPSED_WIDTH); // Allow shrinking
        sidebarRoot.setMaxWidth(EXPANDED_WIDTH);
        
        // Add clipping to prevent overflow during animation
        javafx.scene.shape.Rectangle clip = new javafx.scene.shape.Rectangle();
        clip.widthProperty().bind(sidebarRoot.widthProperty());
        clip.heightProperty().bind(sidebarRoot.heightProperty());
        sidebarRoot.setClip(clip);

        // Initial State is Expanded (Close Icon / X)
        playAnimation(true); 
    }

    public void toggleSidebar() {
        isCollapsed = !isCollapsed;
        
        if (isCollapsed) {
            animateCollapse();
            playAnimation(false); // Become Hamburger (Open)
        } else {
            animateExpand();
            playAnimation(true); // Become X (Close)
        }
    }
    
    private void animateCollapse() {
        // 1. Fade OUT content first
        javafx.animation.FadeTransition ft = new javafx.animation.FadeTransition(Duration.millis(150), profileContainer);
        ft.setFromValue(1.0);
        ft.setToValue(0.0);
        
        javafx.animation.FadeTransition ft2 = new javafx.animation.FadeTransition(Duration.millis(150), lblPlatform);
        ft2.setToValue(0.0);
        
        javafx.animation.FadeTransition ft3 = new javafx.animation.FadeTransition(Duration.millis(150), lblManagement);
        ft3.setToValue(0.0);
        
        javafx.animation.ParallelTransition fadeOut = new javafx.animation.ParallelTransition(ft, ft2, ft3);
        
        // 2. Animate Width
        javafx.animation.Timeline widthCollapse = new javafx.animation.Timeline();
        widthCollapse.getKeyFrames().add(
            new javafx.animation.KeyFrame(Duration.millis(250),
                new javafx.animation.KeyValue(sidebarRoot.prefWidthProperty(), COLLAPSED_WIDTH, javafx.animation.Interpolator.EASE_BOTH),
                new javafx.animation.KeyValue(sidebarRoot.maxWidthProperty(), COLLAPSED_WIDTH, javafx.animation.Interpolator.EASE_BOTH)
            )
        );

        // Sequence: Fade Out -> Shrink
        javafx.animation.SequentialTransition sequence = new javafx.animation.SequentialTransition(fadeOut, widthCollapse);
        sequence.setOnFinished(e -> {
             profileContainer.setVisible(false);
             lblPlatform.setVisible(false);
             lblManagement.setVisible(false);
             
             profileContainer.setManaged(false);
             lblPlatform.setManaged(false);
             lblManagement.setManaged(false);
             
             hamburgerBtn.setAlignment(javafx.geometry.Pos.CENTER);
             updateButtonsToIconOnly();
        });
        sequence.play();
    }
    
    private void animateExpand() {
        // 0. Pre-setup
        profileContainer.setVisible(true);
        lblPlatform.setVisible(true);
        lblManagement.setVisible(true);
        
        profileContainer.setManaged(true); // We might need to keep them managed but opacity 0 to calculate width? 
        // Actually, if we manage them, they take space. 
        // For smooth animation, better to set managed TRUE at the END of width expansion or start?
        // Let's set Managed TRUE at start so layout computes, but opacity 0.
        profileContainer.setManaged(true);
        lblPlatform.setManaged(true);
        lblManagement.setManaged(true);
        
        profileContainer.setOpacity(0);
        lblPlatform.setOpacity(0);
        lblManagement.setOpacity(0);
        
        hamburgerBtn.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        updateButtonsToFull();

        // 1. Animate Width
        javafx.animation.Timeline widthExpand = new javafx.animation.Timeline();
        widthExpand.getKeyFrames().add(
            new javafx.animation.KeyFrame(Duration.millis(250),
                new javafx.animation.KeyValue(sidebarRoot.prefWidthProperty(), EXPANDED_WIDTH, javafx.animation.Interpolator.EASE_BOTH),
                new javafx.animation.KeyValue(sidebarRoot.maxWidthProperty(), EXPANDED_WIDTH, javafx.animation.Interpolator.EASE_BOTH)
            )
        );
        
        // 2. Fade IN content
        javafx.animation.FadeTransition ft = new javafx.animation.FadeTransition(Duration.millis(150), profileContainer);
        ft.setFromValue(0.0);
        ft.setToValue(1.0);
        
        javafx.animation.FadeTransition ft2 = new javafx.animation.FadeTransition(Duration.millis(150), lblPlatform);
        ft2.setToValue(1.0);
        
        javafx.animation.FadeTransition ft3 = new javafx.animation.FadeTransition(Duration.millis(150), lblManagement);
        ft3.setToValue(1.0);
        
        javafx.animation.ParallelTransition fadeIn = new javafx.animation.ParallelTransition(ft, ft2, ft3);

        // Sequence: Grow -> Fade In
        javafx.animation.SequentialTransition sequence = new javafx.animation.SequentialTransition(widthExpand, fadeIn);
        sequence.play();
    }
    
    private void updateButtonsToIconOnly() {
        setButtonCollapsed(btnDashboard, "🏠");
        setButtonCollapsed(btnOffers, "📦");
        setButtonCollapsed(btnGigs, "🧑‍💻");
        setButtonCollapsed(btnCommunity, "💬");
        setButtonCollapsed(btnApplications, "📬");
        setButtonCollapsed(btnHR, "👥");
        setButtonCollapsed(btnLogout, "🚪");
    }

    private void updateButtonsToFull() {
        setButtonExpanded(btnDashboard, "🏠  Dashboard");
        setButtonExpanded(btnOffers, "📦  Offers");
        setButtonExpanded(btnGigs, "🧑‍💻  Gig View");
        setButtonExpanded(btnCommunity, "💬  Community");
        setButtonExpanded(btnApplications, "📬  Applications");
        setButtonExpanded(btnHR, "👥  HR Admin");
        setButtonExpanded(btnLogout, "🚪  Logout");
    }

    private void playAnimation(boolean toCloseIcon) {
        // toCloseIcon = true => Turn into X (Sidebar Expanded)
        // toCloseIcon = false => Turn into Lines (Sidebar Collapsed)

        Duration duration = Duration.millis(300);

        // Line 1
        javafx.animation.RotateTransition rt1 = new javafx.animation.RotateTransition(duration, line1);
        javafx.animation.TranslateTransition tt1 = new javafx.animation.TranslateTransition(duration, line1);
        
        // Line 2
        javafx.animation.FadeTransition ft2 = new javafx.animation.FadeTransition(duration, line2);
        javafx.animation.ScaleTransition st2 = new javafx.animation.ScaleTransition(duration, line2);
        
        // Line 3
        javafx.animation.RotateTransition rt3 = new javafx.animation.RotateTransition(duration, line3);
        javafx.animation.TranslateTransition tt3 = new javafx.animation.TranslateTransition(duration, line3);

        if (toCloseIcon) {
            // Transform to X
            rt1.setToAngle(35);
            tt1.setToY(9); // Move down to center (approx based on spacing)
            
            ft2.setToValue(0);
            st2.setToX(0);
            
            rt3.setToAngle(-35);
            tt3.setToY(-9); // Move up to center
        } else {
            // Transform to Lines
            rt1.setToAngle(0);
            tt1.setToY(0);
            
            ft2.setToValue(1);
            st2.setToX(1);
            
            rt3.setToAngle(0);
            tt3.setToY(0);
        }

        javafx.animation.ParallelTransition pt = new javafx.animation.ParallelTransition(rt1, tt1, ft2, st2, rt3, tt3);
        pt.play();
    }

    private void setButtonCollapsed(Button btn, String icon) {
        btn.setText(icon);
        btn.setStyle("-fx-alignment: CENTER;");
    }
    
    private void setButtonExpanded(Button btn, String text) {
        btn.setText(text);
        btn.setStyle("-fx-alignment: CENTER_LEFT;");
    }

    // Navigation methods
    @FXML private void showDashboard() { if(mainLayoutController!=null) mainLayoutController.showDashboard(); }
    @FXML private void showOffers() { if(mainLayoutController!=null) mainLayoutController.showOffers(); }
    @FXML private void goGigOffers() { if(mainLayoutController!=null) mainLayoutController.showGigOffers(); }
    @FXML private void showForums() { if(mainLayoutController!=null) mainLayoutController.showForums(); }
    @FXML private void showApplicationsAdmin() { if(mainLayoutController!=null) mainLayoutController.showApplicationsAdmin(); }
    @FXML private void showHR() { if(mainLayoutController!=null) mainLayoutController.showHR(); }
    @FXML private void logout() { if(mainLayoutController!=null) mainLayoutController.logout(); }
}
