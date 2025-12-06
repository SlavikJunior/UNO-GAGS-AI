package uno_ui;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.stage.Stage;
import uno_proto.dto.*;

/**
 * MainApp is the entry point for the UNO JavaFX client.
 * 
 * This creates the main window with:
 * - Status label showing game info
 * - Cards display area showing your hand
 * - Action buttons (Play, Draw, Say UNO, Start Game)
 * - Chat area with messages and input
 * 
 * This is kept simple and heavily commented for educational purposes.
 */
public class MainApp extends Application {
    
    // The controller that manages game logic and networking
    private GameController controller;
    
    // UI Components (we keep references so we can update them)
    private Label statusLabel;
    private Label currentCardLabel;
    private FlowPane cardsPane;
    private TextArea chatArea;
    private TextField chatInput;
    private Button playButton;
    private Button drawButton;
    private Button unoButton;
    private Button startButton;
    
    // Track which card button is selected
    private ToggleGroup cardToggleGroup;
    
    /**
     * Main entry point for JavaFX application.
     * This is called by JavaFX framework.
     */
    @Override
    public void start(Stage primaryStage) {
        // Create the controller
        controller = new GameController();
        
        // Set up callbacks so controller can notify us of changes
        controller.setOnStateChanged(this::updateUI);
        controller.setOnChatMessage(this::updateChat);
        
        // Build the UI
        VBox root = buildUI();
        
        // Create the scene and set up the stage (window)
        Scene scene = new Scene(root, 900, 700);
        primaryStage.setTitle("UNO Game Client");
        primaryStage.setScene(scene);
        
        // Handle window close - disconnect from server
        primaryStage.setOnCloseRequest(event -> {
            System.out.println("[MainApp] Window closing, disconnecting...");
            controller.disconnect();
            Platform.exit();
        });
        
        // Show the window
        primaryStage.show();
        
        // Try to connect to server
        connectToServer();
    }
    
    /**
     * Builds the entire UI layout.
     * Returns the root node containing all UI elements.
     */
    private VBox buildUI() {
        VBox root = new VBox(10);
        root.setPadding(new Insets(10));
        
        // Top section: Status and connection info
        VBox topSection = buildTopSection();
        
        // Middle section: Current card display
        VBox cardSection = buildCurrentCardSection();
        
        // Player hand section: Shows cards you can play
        VBox handSection = buildHandSection();
        
        // Action buttons section
        HBox actionsSection = buildActionsSection();
        
        // Chat section at the bottom
        VBox chatSection = buildChatSection();
        
        // Add all sections to root
        root.getChildren().addAll(
            topSection,
            cardSection,
            handSection,
            actionsSection,
            chatSection
        );
        
        return root;
    }
    
    /**
     * Builds the top section with status label and room controls.
     */
    private VBox buildTopSection() {
        VBox section = new VBox(5);
        
        // Status label shows connection state, room info, etc.
        statusLabel = new Label("Not connected");
        statusLabel.setFont(Font.font(14));
        statusLabel.setStyle("-fx-font-weight: bold;");
        
        // Buttons for room management
        HBox roomControls = new HBox(10);
        
        Button createRoomButton = new Button("Create Room");
        createRoomButton.setOnAction(e -> createRoom());
        
        Button joinRoomButton = new Button("Join Room");
        joinRoomButton.setOnAction(e -> joinRoom());
        
        Button getRoomsButton = new Button("Get Rooms");
        getRoomsButton.setOnAction(e -> controller.getRooms());
        
        roomControls.getChildren().addAll(createRoomButton, joinRoomButton, getRoomsButton);
        
        section.getChildren().addAll(statusLabel, roomControls);
        return section;
    }
    
    /**
     * Builds the section showing the current card on the table.
     */
    private VBox buildCurrentCardSection() {
        VBox section = new VBox(5);
        
        Label title = new Label("Current Card:");
        title.setFont(Font.font(12));
        
        currentCardLabel = new Label("(No card yet)");
        currentCardLabel.setFont(Font.font(16));
        currentCardLabel.setStyle("-fx-font-weight: bold;");
        currentCardLabel.setMinHeight(40);
        currentCardLabel.setAlignment(Pos.CENTER);
        
        section.getChildren().addAll(title, currentCardLabel);
        return section;
    }
    
    /**
     * Builds the section showing player's hand (cards).
     */
    private VBox buildHandSection() {
        VBox section = new VBox(5);
        
        Label title = new Label("Your Hand:");
        title.setFont(Font.font(12));
        
        // FlowPane automatically wraps cards to next line
        cardsPane = new FlowPane(10, 10);
        cardsPane.setPrefWrapLength(800);
        cardsPane.setStyle("-fx-border-color: #cccccc; -fx-border-width: 1; -fx-padding: 10;");
        cardsPane.setMinHeight(120);
        
        // Toggle group ensures only one card can be selected at a time
        cardToggleGroup = new ToggleGroup();
        
        // Initially show placeholder cards
        showPlaceholderCards();
        
        section.getChildren().addAll(title, cardsPane);
        VBox.setVgrow(section, Priority.ALWAYS);
        
        return section;
    }
    
    /**
     * Builds the section with action buttons.
     */
    private HBox buildActionsSection() {
        HBox section = new HBox(10);
        section.setAlignment(Pos.CENTER);
        
        // Play Card button
        playButton = new Button("Play Selected Card");
        playButton.setOnAction(e -> playCard());
        playButton.setDisable(true); // Disabled until card is selected
        
        // Draw Card button
        drawButton = new Button("Draw Card");
        drawButton.setOnAction(e -> controller.drawCard());
        
        // Say UNO button
        unoButton = new Button("Say UNO!");
        unoButton.setOnAction(e -> controller.sayUno());
        unoButton.setStyle("-fx-background-color: #ff0000; -fx-text-fill: white; -fx-font-weight: bold;");
        
        // Start Game button
        startButton = new Button("Start Game");
        startButton.setOnAction(e -> controller.startGame());
        startButton.setStyle("-fx-background-color: #00aa00; -fx-text-fill: white; -fx-font-weight: bold;");
        
        section.getChildren().addAll(playButton, drawButton, unoButton, startButton);
        return section;
    }
    
    /**
     * Builds the chat section.
     */
    private VBox buildChatSection() {
        VBox section = new VBox(5);
        
        Label title = new Label("Chat:");
        title.setFont(Font.font(12));
        
        // Text area for displaying chat messages
        chatArea = new TextArea();
        chatArea.setEditable(false);
        chatArea.setPrefHeight(150);
        chatArea.setWrapText(true);
        
        // Input for sending chat messages
        HBox chatInputBox = new HBox(5);
        chatInput = new TextField();
        chatInput.setPromptText("Type a message...");
        chatInput.setPrefWidth(700);
        
        Button sendButton = new Button("Send");
        sendButton.setOnAction(e -> sendChat());
        
        // Also send on Enter key
        chatInput.setOnAction(e -> sendChat());
        
        chatInputBox.getChildren().addAll(chatInput, sendButton);
        HBox.setHgrow(chatInput, Priority.ALWAYS);
        
        section.getChildren().addAll(title, chatArea, chatInputBox);
        return section;
    }
    
    /**
     * Shows some placeholder cards when we don't have real data yet.
     */
    private void showPlaceholderCards() {
        cardsPane.getChildren().clear();
        
        // Create some fake cards for demonstration
        String[] placeholderCards = {
            "Red 5", "Blue 7", "Green Skip", "Yellow 2", 
            "Red Draw+2", "Wild", "Blue Reverse"
        };
        
        for (int i = 0; i < placeholderCards.length; i++) {
            ToggleButton cardButton = createCardButton(placeholderCards[i], i);
            cardsPane.getChildren().add(cardButton);
        }
    }
    
    /**
     * Creates a button representing a card.
     * 
     * @param cardText Text to display on card
     * @param index Index of this card in hand
     * @return A styled ToggleButton
     */
    private ToggleButton createCardButton(String cardText, int index) {
        ToggleButton button = new ToggleButton(cardText);
        button.setToggleGroup(cardToggleGroup);
        button.setPrefSize(100, 60);
        button.setWrapText(true);
        
        // Style the button to look like a card
        button.setStyle(
            "-fx-background-color: #ffffff; " +
            "-fx-border-color: #333333; " +
            "-fx-border-width: 2; " +
            "-fx-border-radius: 5; " +
            "-fx-background-radius: 5;"
        );
        
        // Change appearance when selected
        button.selectedProperty().addListener((obs, wasSelected, isSelected) -> {
            if (isSelected) {
                button.setStyle(
                    "-fx-background-color: #ffff99; " +
                    "-fx-border-color: #ff0000; " +
                    "-fx-border-width: 3; " +
                    "-fx-border-radius: 5; " +
                    "-fx-background-radius: 5;"
                );
                controller.setSelectedCardIndex(index);
                playButton.setDisable(false);
            } else {
                button.setStyle(
                    "-fx-background-color: #ffffff; " +
                    "-fx-border-color: #333333; " +
                    "-fx-border-width: 2; " +
                    "-fx-border-radius: 5; " +
                    "-fx-background-radius: 5;"
                );
                if (controller.getSelectedCardIndex() == index) {
                    playButton.setDisable(true);
                }
            }
        });
        
        return button;
    }
    
    /**
     * Formats a Card object into a display string.
     */
    private String formatCard(Card card) {
        if (card == null) return "(No card)";
        
        String color = card.getColor().toString();
        String type = card.getType().toString();
        
        if (card.getType() == CardType.NUMBER) {
            return color + " " + card.getNumber();
        } else {
            return color + " " + type;
        }
    }
    
    /**
     * Updates the entire UI based on current controller state.
     * This is called whenever game state changes.
     */
    private void updateUI() {
        // Update status label
        StringBuilder status = new StringBuilder();
        
        if (controller.isConnected()) {
            status.append("Connected");
            
            if (controller.getCurrentRoomId() != null) {
                status.append(" | Room: ").append(controller.getCurrentRoomId());
                
                LobbyUpdate lobby = controller.getCurrentLobbyState();
                if (lobby != null) {
                    status.append(" | Players: ").append(lobby.getPlayers().size());
                    status.append(" | Status: ").append(lobby.getRoomStatus());
                }
                
                GameState gameState = controller.getCurrentGameState();
                if (gameState != null) {
                    status.append(" | Current Player: ").append(gameState.getCurrentPlayerId());
                    status.append(" | Phase: ").append(gameState.getGamePhase());
                }
            }
        } else {
            status.append("Not connected");
        }
        
        statusLabel.setText(status.toString());
        
        // Update current card display
        GameState gameState = controller.getCurrentGameState();
        if (gameState != null && gameState.getCurrentCard() != null) {
            currentCardLabel.setText(formatCard(gameState.getCurrentCard()));
            
            // Color the label based on card color
            Card currentCard = gameState.getCurrentCard();
            if (currentCard.getColor() != CardColor.WILD) {
                currentCardLabel.setTextFill(getColorForCard(currentCard.getColor()));
            } else {
                currentCardLabel.setTextFill(Color.BLACK);
            }
        } else {
            currentCardLabel.setText("(No card yet)");
            currentCardLabel.setTextFill(Color.BLACK);
        }
        
        // Note: In a complete implementation, we would update the cards pane
        // with actual cards from controller.getMyHand()
        // For now, we just show placeholder cards
    }
    
    /**
     * Updates the chat area with new messages.
     */
    private void updateChat() {
        StringBuilder chatText = new StringBuilder();
        
        for (ChatMessage msg : controller.getChatMessages()) {
            chatText.append("[")
                    .append(msg.getSenderName())
                    .append("]: ")
                    .append(msg.getContent())
                    .append("\n");
        }
        
        chatArea.setText(chatText.toString());
        
        // Scroll to bottom
        chatArea.setScrollTop(Double.MAX_VALUE);
    }
    
    /**
     * Gets JavaFX Color for a card color.
     */
    private Color getColorForCard(CardColor cardColor) {
        switch (cardColor) {
            case RED: return Color.RED;
            case BLUE: return Color.BLUE;
            case GREEN: return Color.GREEN;
            case YELLOW: return Color.GOLD;
            default: return Color.BLACK;
        }
    }
    
    /**
     * Attempts to connect to the server.
     */
    private void connectToServer() {
        System.out.println("[MainApp] Attempting to connect to server...");
        
        // Try to connect in background so we don't freeze the UI
        new Thread(() -> {
            boolean success = controller.connect();
            
            Platform.runLater(() -> {
                if (success) {
                    statusLabel.setText("Connected to server");
                    showAlert(Alert.AlertType.INFORMATION, "Connected", "Successfully connected to server!");
                } else {
                    statusLabel.setText("Connection failed");
                    showAlert(Alert.AlertType.ERROR, "Connection Failed", 
                        "Could not connect to server. Make sure the server is running on localhost:9090");
                }
            });
        }).start();
    }
    
    /**
     * Shows a dialog to create a room.
     */
    private void createRoom() {
        TextInputDialog dialog = new TextInputDialog("My Room");
        dialog.setTitle("Create Room");
        dialog.setHeaderText("Create a new game room");
        dialog.setContentText("Room name:");
        
        dialog.showAndWait().ifPresent(roomName -> {
            if (!roomName.trim().isEmpty()) {
                controller.createRoom(roomName, 4);
            }
        });
    }
    
    /**
     * Shows a dialog to join a room.
     */
    private void joinRoom() {
        TextInputDialog dialog = new TextInputDialog("1");
        dialog.setTitle("Join Room");
        dialog.setHeaderText("Join an existing room");
        dialog.setContentText("Room ID:");
        
        dialog.showAndWait().ifPresent(roomIdStr -> {
            try {
                long roomId = Long.parseLong(roomIdStr);
                controller.joinRoom(roomId);
            } catch (NumberFormatException e) {
                showAlert(Alert.AlertType.ERROR, "Invalid Input", "Room ID must be a number");
            }
        });
    }
    
    /**
     * Handles playing a card.
     */
    private void playCard() {
        if (controller.getSelectedCardIndex() < 0) {
            showAlert(Alert.AlertType.WARNING, "No Card Selected", "Please select a card to play");
            return;
        }
        
        // For WILD cards, we should ask for color choice
        // For now, just play with null color (works for non-wild cards)
        controller.playCard(null);
        
        // TODO: If the card is WILD, show a dialog to choose color
    }
    
    /**
     * Sends a chat message.
     */
    private void sendChat() {
        String text = chatInput.getText().trim();
        if (!text.isEmpty()) {
            controller.sendChat(text);
            chatInput.clear();
        }
    }
    
    /**
     * Shows a simple alert dialog.
     */
    private void showAlert(Alert.AlertType type, String title, String content) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.showAndWait();
    }
    
    /**
     * Main method - entry point for Java application.
     * This launches the JavaFX application.
     */
    public static void main(String[] args) {
        launch(args);
    }
}
