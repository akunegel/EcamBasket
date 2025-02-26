import javafx.animation.AnimationTimer;
import javafx.application.Application;
import javafx.scene.Group;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.image.Image;
import javafx.scene.input.MouseEvent;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.stage.Stage;
import java.io.FileInputStream;
import java.io.FileNotFoundException;

public class FreeThrowApp extends Application {

    // Gérer compteur de secondes
    private static long startTime = System.currentTimeMillis();
    private static long elapsedTime = (System.currentTimeMillis() - startTime) / 1000;

    // Dimensions de la fenêtre et du sol (pour la physique)
    private static final int WINDOW_WIDTH = 1920;
    private static final int WINDOW_HEIGHT = 1080;
    private static final int FLOOR_Y = 1000;
    
    // Propriétés de la balle
    private static final double BALL_DIAMETER = 80;
    private double ballX = 290;         // Position horizontale initiale
    private double ballY = 500;         // Position verticale initiale
    private double rebounds = 0;

    // Valeurs de base choisies pour que, sans chargement, le lancer soit raté
    private double velocityX = 44;      
    private double velocityY = -21;     
    private final double gravity = 1;   // Accélération due à la gravité

    // Phases de réglage :
    // 0 : ajustement de la vélocité horizontale,
    // 1 : ajustement de la vélocité verticale,
    // 2 : balle lancée.
    private int phase = 0;

    // Variables pour le mécanisme de charge (utilisées en phases 0 et 1)
    private boolean charging = false;
    private double charge = 0;
    private final double maxCharge = 100;
    private final double chargeRate = 1.0;
    private boolean increasing = true; // true si la charge augmente, false sinon

    // Image du joueur
    private Image playerImage;
    private double playerX = 50;                 
    private double playerY = FLOOR_Y - 475;        

    // Image du fond (court.jpg) présente dans le même dossier
    private Image courtImage;

    // Backboard (vertical) : rectangle fixe
    private static final double BACKBOARD_X = 1700;
    private static final double BACKBOARD_Y = 250;
    private static final double BACKBOARD_WIDTH = 20;
    private static final double BACKBOARD_HEIGHT = 150;

    // Panier (objet Basket) placé juste à gauche du backboard.
    // Sa taille reste de 100 x 20.
    private Basket basket = new Basket(1580, 350, 120, 20);

    // Score et détection d'un lancer réussi
    private int score = 0;
    private boolean scoredShot = false;

    @Override
    public void start(Stage primaryStage) {
        primaryStage.setTitle("Free Throw Basket 2D");

        Group root = new Group();
        Canvas canvas = new Canvas(WINDOW_WIDTH, WINDOW_HEIGHT);
        root.getChildren().add(canvas);
        Scene scene = new Scene(root);
        primaryStage.setScene(scene);
        primaryStage.show();

        // Chargement de l'image du joueur
        try {
            playerImage = new Image(new FileInputStream("player.png"));
        } catch (FileNotFoundException e) {
            System.out.println("Player image not found.");
            playerImage = null;
        }

        // Chargement de l'image du court (fond)
        try {
            courtImage = new Image(new FileInputStream("court.jpg"));
        } catch (FileNotFoundException e) {
            System.out.println("Court image not found.");
            courtImage = null;
        }

        // Démarrage de la charge au clic (phase 0 ou 1)
        scene.setOnMousePressed((MouseEvent e) -> {
            if (phase == 0 || phase == 1) {
                charging = true;
                charge = 0;
                increasing = true;
            }
        });

        // Au relâchement, on ajuste la vitesse selon la phase
        scene.setOnMouseReleased((MouseEvent e) -> {
            if (charging) {
                charging = false;
                if (phase == 0) {
                    // Ajustement de la vélocité horizontale avec la charge obtenue
                    velocityX += charge / 500.0;
                    phase = 1;
                } else if (phase == 1) {
                    // Ajustement de la vélocité verticale avec la charge obtenue
                    velocityY -= charge / 5;
                    phase = 2; // La balle est lancée
                }
            }
        });

        GraphicsContext gc = canvas.getGraphicsContext2D();

        // Boucle d'animation : mise à jour et rendu de la scène
        AnimationTimer timer = new AnimationTimer() {
            @Override
            public void handle(long now) {
                elapsedTime = (System.currentTimeMillis() - startTime) / 1000;
                updateBall();
                updateCharge();
                drawScene(gc);

                // Reset compteur et temps après 60 secondes
                if (elapsedTime >= 60) {
                    resetBall();
                    resetGame();
                }
            }
        };
        timer.start();
    }

    // Mise à jour de la position de la balle (uniquement en phase 2)
    private void updateBall() {
        if (phase != 2) return;

        ballX += velocityX / 2;
        ballY += velocityY;
        velocityY += gravity;  // Application de la gravité

        // Collision avec le sol
        if (ballY + BALL_DIAMETER >= FLOOR_Y) {
            ballY = FLOOR_Y - BALL_DIAMETER;
            velocityY *= -0.7; // Rebond avec perte d'énergie
            rebounds += 1;
        }

        // --- Collision avec le backboard (collision verticale) ---
        if (velocityX > 0 &&
            ballX + BALL_DIAMETER >= BACKBOARD_X &&
            ballX < BACKBOARD_X + BACKBOARD_WIDTH &&
            ballY + BALL_DIAMETER > BACKBOARD_Y &&
            ballY < BACKBOARD_Y + BACKBOARD_HEIGHT) {
            ballX = BACKBOARD_X - BALL_DIAMETER;
            velocityX *= -0.4;
        }

        // Détection du score : si le centre de la balle passe dans le panier
        if (!scoredShot) {
            double ballCenterX = ballX + BALL_DIAMETER / 2;
            double ballCenterY = ballY + BALL_DIAMETER / 2;
            if (ballCenterX > basket.x && ballCenterX < basket.x + basket.width &&
                ballCenterY > basket.y && ballCenterY < basket.y + basket.height) {
                score++;
                scoredShot = true;
            }
        }

        // Réinitialisation du lancer lorsque la balle sort de l'écran ou après plusieurs rebonds
        if (ballX > WINDOW_WIDTH || ballX < 0 || velocityX == 0 || rebounds >= 3) {
            resetBall();
        }
    }

    // Mise à jour de la charge pendant que l'utilisateur maintient le clic
    private void updateCharge() {
        if (charging && (phase == 0 || phase == 1)) {
            if (increasing) {
                charge += chargeRate;
                if (charge >= maxCharge) {
                    charge = maxCharge;
                    increasing = false;
                }
            } else {
                charge -= chargeRate;
                if (charge <= 0) {
                    charge = 0;
                    increasing = true;
                }
            }
        }
    }

    // Rendu de la scène
    private void drawScene(GraphicsContext gc) {
        // Utilisation de l'image "court.jpg" comme fond
        if (courtImage != null) {
            gc.drawImage(courtImage, 0, 0, WINDOW_WIDTH, WINDOW_HEIGHT);
        } else {
            // En cas d'absence d'image, on remplit le fond d'une couleur par défaut
            gc.setFill(Color.DARKGRAY);
            gc.fillRect(0, 0, WINDOW_WIDTH, WINDOW_HEIGHT);
        }

        // Backboard
        gc.setFill(Color.WHITE);
        gc.fillRect(BACKBOARD_X, BACKBOARD_Y, BACKBOARD_WIDTH, BACKBOARD_HEIGHT);
        
        // Poteau (support) du backboard
        gc.setFill(Color.DARKGRAY);
        double poleX = BACKBOARD_X + BACKBOARD_WIDTH / 2 - 5;
        double poleY = BACKBOARD_Y + BACKBOARD_HEIGHT;
        gc.fillRect(poleX, poleY, 10, FLOOR_Y - poleY);

        // Panier (objet Basket)
        basket.draw(gc);

        // Balle
        gc.setFill(Color.ORANGE);
        gc.fillOval(ballX, ballY, BALL_DIAMETER, BALL_DIAMETER);

        // Image du joueur
        if (playerImage != null) {
            gc.drawImage(playerImage, playerX, playerY);
        }

        // Compteur de score et de temps
        gc.setFill(Color.WHITE);
        gc.setFont(new Font("Arial", 36));
        gc.fillText("Score: " + score, 50, 50);
        gc.fillText("Time: " + (60 - elapsedTime), 50, 150);

        // Flèches de chargement selon la phase
        if (charging) {
            gc.setStroke(Color.BLUE);
            gc.setLineWidth(5);
            if (phase == 0) {
                double arrowStartX = ballX + BALL_DIAMETER / 2;
                double arrowStartY = ballY - 20;
                double arrowLength = charge;
                gc.strokeLine(arrowStartX, arrowStartY, arrowStartX + arrowLength, arrowStartY);
                double arrowHeadSize = 10;
                gc.strokeLine(arrowStartX + arrowLength, arrowStartY,
                              arrowStartX + arrowLength - arrowHeadSize, arrowStartY - arrowHeadSize);
                gc.strokeLine(arrowStartX + arrowLength, arrowStartY,
                              arrowStartX + arrowLength - arrowHeadSize, arrowStartY + arrowHeadSize);
            } else if (phase == 1) {
                double arrowStartX = ballX + BALL_DIAMETER / 2;
                double arrowStartY = ballY - 20;
                double arrowLength = charge;
                gc.strokeLine(arrowStartX, arrowStartY, arrowStartX, arrowStartY - arrowLength);
                double arrowHeadSize = 10;
                gc.strokeLine(arrowStartX, arrowStartY - arrowLength,
                              arrowStartX - arrowHeadSize, arrowStartY - arrowLength + arrowHeadSize);
                gc.strokeLine(arrowStartX, arrowStartY - arrowLength,
                              arrowStartX + arrowHeadSize, arrowStartY - arrowLength + arrowHeadSize);
            }
        }
    }

    // Réinitialisation des variables pour une nouvelle partie
    private void resetGame() {
        ballX = 290;
        ballY = 500;
        velocityX = 44;
        velocityY = -21;
        phase = 0;
        charging = false;
        charge = 0;
        increasing = true;
        scoredShot = false;
        rebounds = 0;
        score = 0;
        startTime = System.currentTimeMillis();
    }

    // Réinitialisation des variables pour un nouveau lancer
    private void resetBall() {
        ballX = 290;
        ballY = 500;
        velocityX = 44;
        velocityY = -21;
        phase = 0;
        charging = false;
        charge = 0;
        increasing = true;
        scoredShot = false;
        rebounds = 0;
    }

    // Classe représentant le panier (objet) avec détection de collision sur le dessus et dessin
    class Basket {
        double x, y, width, height;

        public Basket(double x, double y, double width, double height) {
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
        }

        // Collision simple si le bas de la balle touche le haut du panier
        public boolean collidesWith(double ballX, double ballY, double ballDiameter) {
            return ballX + ballDiameter > x && ballX < x + width &&
                   ballY + ballDiameter >= y && ballY + ballDiameter <= y + 10;
        }

        // Dessin du panier
        public void draw(GraphicsContext gc) {
            gc.setFill(Color.RED);
            gc.fillRect(x, y, width, height);
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}

