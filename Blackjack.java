import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.*;
import javax.imageio.ImageIO;
import java.io.File;
import java.io.IOException;
import java.awt.image.BufferedImage;

public class Blackjack {
	public static void main(String[] args) {
		SwingUtilities.invokeLater(() -> {
			JFrame frame = new JFrame("Blackjack");
			frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
			frame.setResizable(false);
			BlackjackGamePanel panel = new BlackjackGamePanel();
			Runtime.getRuntime().addShutdownHook(new Thread(panel::stopMusic));
			frame.setContentPane(panel);
			frame.pack();
			frame.setLocationRelativeTo(null);
			frame.setVisible(true);
		});
	}

	// --- Game Panel (draws cards and handles UI) ---
	static class BlackjackGamePanel extends JPanel {
		private final Deck deck = new Deck();
		private final Hand player = new Hand();
		private final Hand dealer = new Hand();
		private final JButton hitBtn = new JButton("Hit");
		private final JButton standBtn = new JButton("Stand");
		private final JButton newBtn = new JButton("New Game");
		private final JButton peekBtn = new JButton("Peek");
		private final JButton discardBtn = new JButton("Discard");
		private final JButton luckyBtn = new JButton("Lucky Hit");
		private final JLabel status = new JLabel(" ");

		private enum Phase {PLAYER_TURN, DEALER_TURN, ROUND_OVER}
		private Phase phase = Phase.PLAYER_TURN;

		private static final int CARD_W = 180;
		private static final int CARD_H = 270;
		private static final int DEALER_Y = 75;
		private static final int PLAYER_Y = 455;
		private static final int CARD_START_X = 1160;
		private static final int CARD_START_Y = 300;
		private final Map<String, BufferedImage> cardImages = new HashMap<>();
		private final java.util.List<DealAnimation> dealAnimations = new ArrayList<>();
		private final javax.swing.Timer animationTimer;
		private BufferedImage cardBack = null;
		private BufferedImage tableBackground = null;
		private Process musicProcess = null;
		private boolean peekUsed = false;
		private boolean discardUsed = false;
		private boolean luckyUsed = false;
		private boolean revealDealerCard = false;

		BlackjackGamePanel() {
			setPreferredSize(new Dimension(1400, 900));
			setBackground(new Color(0, 120, 0));
			setLayout(null);

			hitBtn.setBounds(60, 820, 150, 50);
			standBtn.setBounds(230, 820, 150, 50);
			newBtn.setBounds(400, 820, 180, 50);
			peekBtn.setBounds(60, 760, 150, 44);
			discardBtn.setBounds(230, 760, 180, 44);
			luckyBtn.setBounds(430, 760, 180, 44);
			status.setBounds(640, 820, 680, 50);
			status.setForeground(Color.WHITE);
			status.setFont(status.getFont().deriveFont(Font.BOLD, 24f));
			status.setOpaque(true);
			status.setBackground(new Color(0, 0, 0, 90));
			status.setBorder(BorderFactory.createCompoundBorder(
					BorderFactory.createLineBorder(new Color(220, 178, 74), 2),
					BorderFactory.createEmptyBorder(0, 18, 0, 18)
			));
			styleCasinoButton(hitBtn, new Color(24, 115, 61));
			styleCasinoButton(standBtn, new Color(132, 25, 32));
			styleCasinoButton(newBtn, new Color(163, 119, 29));
			styleCasinoButton(peekBtn, new Color(35, 92, 150));
			styleCasinoButton(discardBtn, new Color(112, 67, 150));
			styleCasinoButton(luckyBtn, new Color(31, 128, 135));

			add(hitBtn);
			add(standBtn);
			add(newBtn);
			add(peekBtn);
			add(discardBtn);
			add(luckyBtn);
			add(status);

			hitBtn.addActionListener(e -> onHit());
			standBtn.addActionListener(e -> onStand());
			newBtn.addActionListener(e -> startNewRound());
			peekBtn.addActionListener(e -> onPeek());
			discardBtn.addActionListener(e -> onDiscard());
			luckyBtn.addActionListener(e -> onLuckyHit());
			animationTimer = new javax.swing.Timer(16, e -> updateDealAnimations());

			startNewRound();
			loadCardImages();
			startSmoothCriminalMusic();
		}

		private void styleCasinoButton(JButton button, Color color) {
			button.setFocusPainted(false);
			button.setForeground(Color.WHITE);
			button.setBackground(color);
			button.setFont(button.getFont().deriveFont(Font.BOLD, 20f));
			button.setBorder(BorderFactory.createCompoundBorder(
					BorderFactory.createLineBorder(new Color(238, 201, 102), 2),
					BorderFactory.createEmptyBorder(6, 14, 6, 14)
			));
		}

		private int cardX(int index) {
			return 60 + index * (CARD_W + 24);
		}

		private void animateDeal(Card card, boolean dealerCard, int index) {
			int endX = cardX(index);
			int endY = dealerCard ? DEALER_Y : PLAYER_Y;
			dealAnimations.add(new DealAnimation(card, dealerCard, CARD_START_X, CARD_START_Y, endX, endY));
			if (!animationTimer.isRunning()) animationTimer.start();
		}

		private boolean isAnimating(Card card, boolean dealerCard) {
			for (DealAnimation animation : dealAnimations) {
				if (animation.card == card && animation.dealerCard == dealerCard) return true;
			}
			return false;
		}

		private void updateDealAnimations() {
			dealAnimations.removeIf(DealAnimation::isDone);
			if (dealAnimations.isEmpty()) animationTimer.stop();
			repaint();
		}

		private void startSmoothCriminalMusic() {
			File song = new File("Michael Jackson - Smooth Criminal (Official Video).mp3");
			if (!song.isFile()) {
				return;
			}

			String script = ""
					+ "Add-Type -AssemblyName PresentationCore; "
					+ "$player = New-Object System.Windows.Media.MediaPlayer; "
					+ "$player.Open([Uri]::new('" + song.getAbsolutePath().replace("'", "''") + "')); "
					+ "$player.Volume = 0.35; "
					+ "$player.add_MediaEnded({ $player.Position = [TimeSpan]::Zero; $player.Play() }); "
					+ "$player.Play(); "
					+ "while ($true) { Start-Sleep -Seconds 1 }";

			try {
				musicProcess = new ProcessBuilder(
						"powershell.exe",
						"-NoProfile",
						"-Sta",
						"-WindowStyle", "Hidden",
						"-ExecutionPolicy", "Bypass",
						"-Command", script
				).start();
			} catch (IOException ex) {
				musicProcess = null;
			}
		}

		void stopMusic() {
			if (musicProcess != null) {
				musicProcess.destroy();
				musicProcess = null;
			}
		}

		private void loadCardImages() {
			// Attempt to load images named 2.png..10.png, Jack.png, Queen.png, King.png, Ace.png
			String[] ranks = {"2","3","4","5","6","7","8","9","10","J","Q","K","A"};
			Map<String,String> fileMap = new HashMap<>();
			for (String r : ranks) fileMap.put(r, r + ".png");
			fileMap.put("J", "Jack.png");
			fileMap.put("Q", "Queen.png");
			fileMap.put("K", "King.png");
			fileMap.put("A", "Ace.png");
			for (Map.Entry<String,String> e : fileMap.entrySet()) {
				try {
					BufferedImage img = ImageIO.read(new File(e.getValue()));
					if (img != null) cardImages.put(e.getKey(), img);
				} catch (Exception ex) {
					// ignore, fallback to painted cards
				}
			}
			// optional back image and table background (use Background.png as casino table)
			try {
				tableBackground = ImageIO.read(new File("Background.png"));
			} catch (Exception ex) {
				tableBackground = null;
			}
			// if there's a specific card back image, try to load it (file name cardback.png)
			try { cardBack = ImageIO.read(new File("cardback.png")); } catch (Exception ex) { cardBack = null; }
		}

		private void startNewRound() {
			deck.resetAndShuffle();
			player.clear();
			dealer.clear();
			dealAnimations.clear();
			peekUsed = false;
			discardUsed = false;
			luckyUsed = false;
			revealDealerCard = false;
			player.addCard(deck.draw());
			dealer.addCard(deck.draw());
			player.addCard(deck.draw());
			dealer.addCard(deck.draw());
			animateDeal(player.get(0), false, 0);
			animateDeal(dealer.get(0), true, 0);
			animateDeal(player.get(1), false, 1);
			animateDeal(dealer.get(1), true, 1);
			phase = Phase.PLAYER_TURN;
			status.setText("Your turn");
			hitBtn.setEnabled(true);
			standBtn.setEnabled(true);
			updatePowerupButtons();
			repaint();
		}

		private void onHit() {
			if (phase != Phase.PLAYER_TURN) return;
			player.addCard(deck.draw());
			animateDeal(player.get(player.size() - 1), false, player.size() - 1);
			if (player.getValue() > 21) {
				phase = Phase.ROUND_OVER;
				status.setText("You busted — You lose.");
				hitBtn.setEnabled(false);
				standBtn.setEnabled(false);
			} else if (player.getValue() == 21) {
				onStand();
			} else {
				status.setText("Your turn");
			}
			updatePowerupButtons();
			repaint();
		}

		private void onPeek() {
			if (phase != Phase.PLAYER_TURN || peekUsed) return;
			peekUsed = true;
			revealDealerCard = true;
			status.setText("Peek used: dealer has " + dealer.get(1));
			updatePowerupButtons();
			repaint();
		}

		private void onDiscard() {
			if (phase != Phase.PLAYER_TURN || discardUsed || player.size() <= 2) return;
			Card removed = player.removeLast();
			dealAnimations.removeIf(animation -> animation.card == removed && !animation.dealerCard);
			discardUsed = true;
			status.setText("Discard used: removed " + removed + ". Your turn");
			updatePowerupButtons();
			repaint();
		}

		private void onLuckyHit() {
			if (phase != Phase.PLAYER_TURN || luckyUsed) return;
			luckyUsed = true;
			player.addCard(deck.drawBestFor(player.getValue()));
			animateDeal(player.get(player.size() - 1), false, player.size() - 1);
			if (player.getValue() > 21) {
				phase = Phase.ROUND_OVER;
				status.setText("You busted - You lose.");
				hitBtn.setEnabled(false);
				standBtn.setEnabled(false);
			} else if (player.getValue() == 21) {
				status.setText("Lucky Hit gave you 21!");
				updatePowerupButtons();
				repaint();
				onStand();
				return;
			} else {
				status.setText("Lucky Hit used. Your turn");
			}
			updatePowerupButtons();
			repaint();
		}

		private void updatePowerupButtons() {
			boolean canUsePowerups = phase == Phase.PLAYER_TURN;
			peekBtn.setEnabled(canUsePowerups && !peekUsed);
			discardBtn.setEnabled(canUsePowerups && !discardUsed && player.size() > 2);
			luckyBtn.setEnabled(canUsePowerups && !luckyUsed);
		}

		private void onStand() {
			if (phase != Phase.PLAYER_TURN) return;
			phase = Phase.DEALER_TURN;
			hitBtn.setEnabled(false);
			standBtn.setEnabled(false);
			updatePowerupButtons();
			// dealer draws until 17+
			javax.swing.Timer t = new javax.swing.Timer(400, null);
			t.addActionListener(new ActionListener() {
				public void actionPerformed(ActionEvent ev) {
					if (dealer.getValue() < 17) {
						dealer.addCard(deck.draw());
						animateDeal(dealer.get(dealer.size() - 1), true, dealer.size() - 1);
						status.setText("Dealer draws...");
						repaint();
					} else {
						t.stop();
						concludeRound();
					}
				}
			});
			t.setInitialDelay(0);
			t.start();
		}

		private void concludeRound() {
			int pv = player.getValue();
			int dv = dealer.getValue();
			if (pv > 21) {
				status.setText("You busted — You lose.");
			} else if (dv > 21) {
				status.setText("Dealer busted — You win!");
			} else if (player.isBlackjack() && !dealer.isBlackjack()) {
				status.setText("Blackjack! You win!");
			} else if (!player.isBlackjack() && dealer.isBlackjack()) {
				status.setText("Dealer has Blackjack — You lose.");
			} else if (pv > dv) {
				status.setText("You win (" + pv + " vs " + dv + ")");
			} else if (pv < dv) {
				status.setText("You lose (" + pv + " vs " + dv + ")");
			} else {
				status.setText("Push (tie) — both " + pv);
			}
			phase = Phase.ROUND_OVER;
			updatePowerupButtons();
			repaint();
		}

		@Override
		protected void paintComponent(Graphics g) {
			super.paintComponent(g);
			Graphics2D g2 = (Graphics2D) g;
			g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

			if (tableBackground != null) {
				g2.drawImage(tableBackground, 0, 0, getWidth(), getHeight(), null);
			} else {
				drawCasinoTable(g2);
			}
			drawDeckShoe(g2);
			drawChipStack(g2, 1110, 650, new Color(190, 28, 42), 4);
			drawChipStack(g2, 1210, 680, new Color(28, 92, 176), 3);
			drawChipStack(g2, 1285, 630, new Color(38, 135, 70), 5);
			drawChipStack(g2, 970, 705, new Color(230, 230, 230), 3);
			drawTableLettering(g2);

			// Dealer area
			g2.setColor(new Color(246, 235, 188));
			g2.setFont(g2.getFont().deriveFont(Font.BOLD, 28f));
			g2.drawString("Dealer:", 60, 50);
			for (int i = 0; i < dealer.size(); i++) {
				if (isAnimating(dealer.get(i), true)) continue;
				int x = cardX(i);
				int y = DEALER_Y;
				if (i == 1 && phase == Phase.PLAYER_TURN && !revealDealerCard) {
					drawCardBack(g2, x, y);
				} else {
					drawCard(g2, dealer.get(i), x, y);
				}
			}

			// Player area
			g2.setColor(new Color(246, 235, 188));
			g2.setFont(g2.getFont().deriveFont(Font.BOLD, 28f));
			g2.drawString("Player:", 60, 430);
			for (int i = 0; i < player.size(); i++) {
				if (isAnimating(player.get(i), false)) continue;
				int x = cardX(i);
				int y = PLAYER_Y;
				drawCard(g2, player.get(i), x, y);
			}

			// Values
			g2.setColor(new Color(246, 235, 188));
			g2.setFont(g2.getFont().deriveFont(Font.BOLD, 28f));
			g2.drawString("Player value: " + player.getValue(), 1050, 590);
			if (phase != Phase.PLAYER_TURN) {
				g2.drawString("Dealer value: " + dealer.getValue(), 1050, 210);
			} else {
				g2.drawString("Dealer value: ?", 1050, 210);
			}

			for (DealAnimation animation : dealAnimations) {
				animation.draw(g2);
			}
		}

		private void drawCasinoTable(Graphics2D g2) {
			g2.setPaint(new GradientPaint(0, 0, new Color(5, 70, 42), 0, getHeight(), new Color(2, 35, 24)));
			g2.fillRect(0, 0, getWidth(), getHeight());

			g2.setColor(new Color(4, 94, 52, 140));
			for (int y = 0; y < getHeight(); y += 18) {
				g2.drawLine(0, y, getWidth(), y + 80);
			}

			g2.setColor(new Color(54, 24, 13));
			g2.fillRoundRect(18, 18, getWidth() - 36, getHeight() - 36, 48, 48);
			g2.setPaint(new GradientPaint(0, 28, new Color(191, 132, 48), 0, 90, new Color(95, 49, 21)));
			g2.fillRoundRect(34, 34, getWidth() - 68, getHeight() - 68, 42, 42);
			g2.setPaint(new GradientPaint(0, 70, new Color(9, 94, 53), 0, getHeight() - 120, new Color(2, 56, 35)));
			g2.fillRoundRect(58, 58, getWidth() - 116, getHeight() - 116, 34, 34);

			g2.setColor(new Color(240, 204, 107, 150));
			g2.setStroke(new BasicStroke(3f));
			g2.drawRoundRect(78, 78, getWidth() - 156, getHeight() - 156, 26, 26);
			g2.setStroke(new BasicStroke(1f));
		}

		private void drawTableLettering(Graphics2D g2) {
			g2.setFont(g2.getFont().deriveFont(Font.BOLD, 54f));
			g2.setColor(new Color(255, 225, 130, 48));
			g2.drawString("BLACKJACK", 500, 390);
			g2.setFont(g2.getFont().deriveFont(Font.BOLD, 22f));
			g2.drawString("PAYS 3 TO 2", 585, 425);
			g2.drawArc(360, 250, 520, 260, 200, 140);
		}

		private void drawDeckShoe(Graphics2D g2) {
			int x = CARD_START_X - 25;
			int y = CARD_START_Y - 40;
			g2.setColor(new Color(0, 0, 0, 90));
			g2.fillRoundRect(x + 8, y + 10, 170, 110, 18, 18);
			g2.setPaint(new GradientPaint(x, y, new Color(38, 38, 42), x + 170, y + 110, new Color(10, 10, 12)));
			g2.fillRoundRect(x, y, 170, 110, 18, 18);
			g2.setColor(new Color(230, 190, 88));
			g2.setStroke(new BasicStroke(3f));
			g2.drawRoundRect(x, y, 170, 110, 18, 18);
			g2.setStroke(new BasicStroke(1f));
			g2.setColor(new Color(245, 245, 245));
			for (int i = 0; i < 5; i++) {
				g2.fillRoundRect(x + 24 + i * 18, y + 24 + i * 3, 70, 44, 8, 8);
				g2.setColor(new Color(40, 48, 72));
				g2.drawRoundRect(x + 24 + i * 18, y + 24 + i * 3, 70, 44, 8, 8);
				g2.setColor(new Color(245, 245, 245));
			}
		}

		private void drawChipStack(Graphics2D g2, int x, int y, Color color, int count) {
			for (int i = count - 1; i >= 0; i--) {
				drawChip(g2, x, y - i * 10, color);
			}
		}

		private void drawChip(Graphics2D g2, int x, int y, Color color) {
			g2.setColor(new Color(0, 0, 0, 70));
			g2.fillOval(x + 5, y + 7, 70, 70);
			g2.setColor(color);
			g2.fillOval(x, y, 70, 70);
			g2.setColor(Color.WHITE);
			for (int i = 0; i < 8; i++) {
				double angle = Math.toRadians(i * 45);
				int sx = x + 35 + (int) Math.round(Math.cos(angle) * 24);
				int sy = y + 35 + (int) Math.round(Math.sin(angle) * 24);
				g2.fillRect(sx - 4, sy - 8, 8, 16);
			}
			g2.setColor(new Color(245, 220, 128));
			g2.fillOval(x + 17, y + 17, 36, 36);
			g2.setColor(new Color(45, 35, 20));
			g2.setFont(g2.getFont().deriveFont(Font.BOLD, 14f));
			g2.drawString("$25", x + 23, y + 40);
		}

		private void drawCardBack(Graphics2D g2, int x, int y) {
			if (cardBack != null) {
				g2.drawImage(cardBack, x, y, CARD_W, CARD_H, null);
				return;
			}
			g2.setColor(new Color(0, 0, 0, 80));
			g2.fillRoundRect(x + 8, y + 10, CARD_W, CARD_H, 18, 18);
			g2.setPaint(new GradientPaint(x, y, new Color(20, 35, 115), x + CARD_W, y + CARD_H, new Color(90, 15, 80)));
			g2.fillRoundRect(x, y, CARD_W, CARD_H, 18, 18);
			g2.setColor(new Color(238, 201, 102));
			g2.setStroke(new BasicStroke(4f));
			g2.drawRoundRect(x, y, CARD_W, CARD_H, 18, 18);
			g2.setStroke(new BasicStroke(2f));
			g2.drawRoundRect(x + 16, y + 16, CARD_W - 32, CARD_H - 32, 12, 12);
			g2.setFont(g2.getFont().deriveFont(Font.BOLD, 26f));
			g2.drawString("CASINO", x + 42, y + 120);
			g2.setFont(g2.getFont().deriveFont(Font.BOLD, 44f));
			g2.drawString("21", x + 65, y + 170);
			g2.setStroke(new BasicStroke(1f));
		}

		private void drawCard(Graphics2D g2, Card c, int x, int y) {
			BufferedImage img = cardImages.get(c.rank);
			if (img != null) {
				g2.drawImage(img, x, y, CARD_W, CARD_H, null);
				return;
			}
			g2.setColor(new Color(0, 0, 0, 80));
			g2.fillRoundRect(x + 8, y + 10, CARD_W, CARD_H, 18, 18);
			g2.setPaint(new GradientPaint(x, y, Color.WHITE, x + CARD_W, y + CARD_H, new Color(220, 230, 245)));
			g2.fillRoundRect(x, y, CARD_W, CARD_H, 18, 18);
			g2.setColor(new Color(20, 20, 20));
			g2.setStroke(new BasicStroke(3f));
			g2.drawRoundRect(x, y, CARD_W, CARD_H, 18, 18);
			g2.setStroke(new BasicStroke(1f));
			g2.setFont(g2.getFont().deriveFont(Font.BOLD, 38f));
			Color suitColor = ("♥".equals(c.suit) || "♦".equals(c.suit)) ? Color.RED : Color.BLACK;
			g2.setColor(suitColor);
			g2.drawString(c.rank, x + 16, y + 46);
			g2.drawString(c.suit, x + 16, y + 88);
			g2.setFont(g2.getFont().deriveFont(Font.BOLD, 92f));
			g2.drawString(c.suit, x + 60, y + 175);
			g2.setFont(g2.getFont().deriveFont(Font.BOLD, 38f));
			g2.drawString(c.rank, x + CARD_W - 60, y + CARD_H - 24);
		}

		private class DealAnimation {
			private static final int DURATION_MS = 420;
			private final Card card;
			private final boolean dealerCard;
			private final int startX;
			private final int startY;
			private final int endX;
			private final int endY;
			private final long startTime = System.currentTimeMillis();

			DealAnimation(Card card, boolean dealerCard, int startX, int startY, int endX, int endY) {
				this.card = card;
				this.dealerCard = dealerCard;
				this.startX = startX;
				this.startY = startY;
				this.endX = endX;
				this.endY = endY;
			}

			boolean isDone() {
				return progress() >= 1.0;
			}

			private double progress() {
				double raw = (System.currentTimeMillis() - startTime) / (double) DURATION_MS;
				return Math.min(1.0, raw);
			}

			void draw(Graphics2D g2) {
				double p = progress();
				double eased = 1 - Math.pow(1 - p, 3);
				int x = (int) Math.round(startX + (endX - startX) * eased);
				int y = (int) Math.round(startY + (endY - startY) * eased - Math.sin(p * Math.PI) * 60);
				double scale = 0.72 + 0.28 * eased;

				Graphics2D copy = (Graphics2D) g2.create();
				copy.translate(x + CARD_W / 2.0, y + CARD_H / 2.0);
				copy.scale(scale, scale);
				copy.rotate(Math.toRadians((1 - p) * 10));
				copy.translate(-CARD_W / 2.0, -CARD_H / 2.0);
				if (dealerCard && card == dealer.get(1) && phase == Phase.PLAYER_TURN) {
					drawCardBack(copy, 0, 0);
				} else {
					drawCard(copy, card, 0, 0);
				}
				copy.dispose();
			}
		}

	}

	// --- core game model classes ---
	static class Card {
		final String rank;
		final String suit;

		Card(String rank, String suit) { this.rank = rank; this.suit = suit; }

		int value() {
			if ("J".equals(rank) || "Q".equals(rank) || "K".equals(rank)) return 10;
			if ("A".equals(rank)) return 11;
			return Integer.parseInt(rank);
		}

		public String toString() { return rank + suit; }
	}

	static class Deck {
		private final LinkedList<Card> cards = new LinkedList<>();
		private final String[] suits = {"♠", "♥", "♦", "♣"};
		private final String[] ranks = {"2","3","4","5","6","7","8","9","10","J","Q","K","A"};
		Deck() { resetAndShuffle(); }

		void resetAndShuffle() {
			cards.clear();
			for (String s : suits) for (String r : ranks) cards.add(new Card(r, s));
			Collections.shuffle(cards, new Random());
		}

		Card draw() {
			if (cards.isEmpty()) throw new NoSuchElementException("Deck is empty");
			return cards.removeFirst();
		}

		Card drawBestFor(int currentValue) {
			if (cards.isEmpty()) throw new NoSuchElementException("Deck is empty");
			Card best = null;
			int bestValue = -1;
			for (Card card : cards) {
				int value = card.value();
				if ("A".equals(card.rank) && currentValue + value > 21) value = 1;
				int total = currentValue + value;
				if (total <= 21 && total > bestValue) {
					best = card;
					bestValue = total;
				}
			}
			if (best != null) {
				cards.remove(best);
				return best;
			}
			return draw();
		}
	}

	static class Hand {
		private final java.util.List<Card> cards = new java.util.ArrayList<>();
		void addCard(Card c) { cards.add(c); }
		void clear() { cards.clear(); }
		Card get(int i) { return cards.get(i); }
		Card removeLast() { return cards.remove(cards.size() - 1); }
		int size() { return cards.size(); }
		int getValue() {
			int total = 0, aces = 0;
			for (Card c : cards) {
				total += c.value();
				if ("A".equals(c.rank)) aces++;
			}
			while (total > 21 && aces > 0) { total -= 10; aces--; }
			return total;
		}
		boolean isBlackjack() { return cards.size() == 2 && getValue() == 21; }
	}
}
