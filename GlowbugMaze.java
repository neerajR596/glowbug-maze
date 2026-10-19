import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.*;
import java.util.*;

public class GlowbugMaze extends JPanel implements ActionListener {
    // # wall, . dot, * freeze crystal, ' ' empty, P player start, E enemy start, - enemy gate
    static final String[] MAP = {
        "#####################",
        "#*.......#.#.......*#",
        "#.###.##.#.#.##.###.#",
        "#...................#",
        "#.###.#.#####.#.###.#",
        "#.....#...#...#.....#",
        "#####.###.#.###.#####",
        "    #.#.......#.#    ",
        "#####.#.##-##.#.#####",
        "#.......#EEE#.......#",
        "#####.#.#####.#.#####",
        "    #.#.......#.#    ",
        "#####.#.#####.#.#####",
        "#.........#.........#",
        "#.##.####.#.####.##.#",
        "#*.#......P......#.*#",
        "##.#.#.#######.#.#.##",
        "#....#....#....#....#",
        "#.#######.#.#######.#",
        "#...................#",
        "#####################"
    };
    static final int W = 21, H = 21, T = 28, TOP = 44, BOTTOM = 40;
    static final int[] DX = {1, 0, -1, 0}, DY = {0, 1, 0, -1}; // right, down, left, up
    static final Color[] EN_COL = {new Color(255, 70, 90), new Color(255, 150, 40), new Color(190, 90, 255), new Color(40, 210, 160)};
    static final int STEPS = 7; // sub-steps per tile (smooth movement)

    char[][] grid = new char[H][W];
    int dotsLeft, score, lives, level = 1, hiScore;
    int px, py, pdir, pwant, psub;            // player tile, direction, wanted dir, sub-step progress
    int[] ex = new int[4], ey = new int[4], edir = new int[4], esub = new int[4], eHome = new int[4];
    boolean[] eDead = new boolean[4];
    int freezeTicks = 0, combo = 0, tick = 0, readyTicks = 0, deathTicks = 0, enemyTickEvery = 1;
    int startPx, startPy, gateX, gateY;
    int[][] eStart = new int[4][2];
    boolean gameOver = false, paused = false, levelClear = false, pMoving = false;
    Random rnd = new Random();
    javax.swing.Timer timer = new javax.swing.Timer(16, this);

    public GlowbugMaze() {
        setPreferredSize(new Dimension(W * T, TOP + H * T + BOTTOM));
        setBackground(new Color(8, 10, 24));
        setFocusable(true);
        addKeyListener(new KeyAdapter() { public void keyPressed(KeyEvent e) { key(e.getKeyCode()); } });
        newGame();
        timer.start();
    }

    void newGame() { score = 0; lives = 3; level = 1; gameOver = false; loadLevel(); }

    void loadLevel() {
        dotsLeft = 0; int k = 0;
        for (int y = 0; y < H; y++) for (int x = 0; x < W; x++) {
            char c = MAP[y].charAt(x);
            if (c == 'P') { startPx = x; startPy = y; c = ' '; }
            if (c == 'E') { if (k < 4) { eStart[k][0] = x; eStart[k][1] = y; k++; } c = ' '; }
            if (c == '-') { gateX = x; gateY = y; }
            if (c == '.' || c == '*') dotsLeft++;
            grid[y][x] = c;
        }
        eStart[3][0] = gateX; eStart[3][1] = gateY - 1; // 4th enemy starts outside
        levelClear = false;
        resetPositions();
    }

    void resetPositions() {
        px = startPx; py = startPy; pdir = 2; pwant = 2; psub = 0; pMoving = false;
        for (int i = 0; i < 4; i++) { ex[i] = eStart[i][0]; ey[i] = eStart[i][1]; edir[i] = 3; esub[i] = 0; eDead[i] = false; eHome[i] = i * 90; }
        freezeTicks = 0; readyTicks = 90; deathTicks = 0;
    }

    boolean wall(int x, int y, boolean enemy) {
        if (y < 0 || y >= H) return true;
        x = (x + W) % W;
        char c = grid[y][x];
        return c == '#' || (c == '-' && !enemy);
    }

    void key(int k) {
        if (gameOver) { if (k == KeyEvent.VK_ENTER) newGame(); return; }
        if (k == KeyEvent.VK_P) { paused = !paused; repaint(); return; }
        int before = pwant; pwant = -1;
        if (k == KeyEvent.VK_RIGHT || k == KeyEvent.VK_D) pwant = 0;
        if (k == KeyEvent.VK_DOWN || k == KeyEvent.VK_S) pwant = 1;
        if (k == KeyEvent.VK_LEFT || k == KeyEvent.VK_A) pwant = 2;
        if (k == KeyEvent.VK_UP || k == KeyEvent.VK_W) pwant = 3;
        if (pwant < 0) pwant = before; else pMoving = true;
    }

    public void actionPerformed(ActionEvent e) { step(); repaint(); }

    void step() {
        if (gameOver || paused) return;
        tick++;
        if (readyTicks > 0) { readyTicks--; return; }
        if (deathTicks > 0) { if (--deathTicks == 0) { if (lives <= 0) gameOver = true; else resetPositions(); } return; }
        if (levelClear) { level++; loadLevel(); return; }

        if (!pMoving) return;
        movePlayer();
        for (int i = 0; i < 4; i++) {
            if (eHome[i] > 0) { eHome[i]--; continue; }
            int moves = eDead[i] ? 2 : freezeTicks > 0 ? (tick % 2) : (level < 3 && tick % 5 == 0 ? 0 : 1);
            for (int m = 0; m < moves; m++) moveEnemy(i);
        }
        if (freezeTicks > 0 && --freezeTicks == 0) combo = 0;
        collide();
    }

    void movePlayer() {
        if (!pMoving) return;
        if (psub == 0) {
            if (pwant != pdir && !wall(px + DX[pwant], py + DY[pwant], false)) pdir = pwant;
            if (wall(px + DX[pdir], py + DY[pdir], false)) return;
        } else if (pwant == (pdir + 2) % 4) { // instant reverse mid-tile
            px = (px + DX[pdir] + W) % W; py += DY[pdir]; pdir = pwant; psub = STEPS - psub; return;
        }
        if (++psub >= STEPS) {
            psub = 0; px = (px + DX[pdir] + W) % W; py += DY[pdir];
            char c = grid[py][px];
            if (c == '.') { score += 10; grid[py][px] = ' '; dotsLeft--; }
            if (c == '*') { score += 50; grid[py][px] = ' '; dotsLeft--; freezeTicks = Math.max(180, 420 - level * 40); combo = 0; }
            if (dotsLeft == 0) { levelClear = true; readyTicks = 60; }
            hiScore = Math.max(hiScore, score);
        }
    }

    void moveEnemy(int i) {
        if (esub[i] == 0) { chooseDir(i); if (wall(ex[i] + DX[edir[i]], ey[i] + DY[edir[i]], true)) return; }
        if (++esub[i] < STEPS) return;
        esub[i] = 0;
        ex[i] = (ex[i] + DX[edir[i]] + W) % W; ey[i] += DY[edir[i]];
        if (eDead[i] && ex[i] == gateX && ey[i] == gateY + 1) { eDead[i] = false; eHome[i] = 60; edir[i] = 3; }
    }

    void chooseDir(int i) {
        int tx, ty;
        if (eDead[i]) { tx = gateX; ty = gateY + 1; }
        else if (grid[ey[i]][ex[i]] == '-' || (ey[i] == gateY + 1 && Math.abs(ex[i] - gateX) <= 1)) { tx = gateX; ty = gateY - 2; } // leave pen
        else if (freezeTicks > 0) { tx = rnd.nextInt(W); ty = rnd.nextInt(H); }
        else switch (i) {
            case 0: tx = px; ty = py; break;                                        // Hunter: chases directly
            case 1: tx = px + 4 * DX[pdir]; ty = py + 4 * DY[pdir]; break;          // Ambusher: aims ahead of you
            case 2: { int d = Math.abs(ex[i] - px) + Math.abs(ey[i] - py);          // Shy: chases, backs off when close
                      if (d > 7) { tx = px; ty = py; } else { tx = 0; ty = H - 1; } break; }
            default: if (rnd.nextInt(3) == 0) { tx = rnd.nextInt(W); ty = rnd.nextInt(H); } else { tx = px; ty = py; } // Wanderer
        }
        int best = -1; double bestD = 1e9;
        for (int d = 0; d < 4; d++) {
            if (d == (edir[i] + 2) % 4) continue;
            int nx = ex[i] + DX[d], ny = ey[i] + DY[d];
            if (wall(nx, ny, true)) continue;
            if (!eDead[i] && grid[ny][(nx + W) % W] == '-' && d == 1) continue; // don't re-enter pen
            double dist = Math.hypot(nx - tx, ny - ty);
            if (dist < bestD) { bestD = dist; best = d; }
        }
        if (best < 0) best = (edir[i] + 2) % 4;
        edir[i] = best;
    }

    double fx(int tx, int dir, int sub) { return tx + DX[dir] * (double) sub / STEPS; }
    double fy(int ty, int dir, int sub) { return ty + DY[dir] * (double) sub / STEPS; }

    void collide() {
        double ppx = fx(px, pdir, psub), ppy = fy(py, pdir, psub);
        for (int i = 0; i < 4; i++) {
            if (eDead[i]) continue;
            double d = Math.hypot(ppx - fx(ex[i], edir[i], esub[i]), ppy - fy(ey[i], edir[i], esub[i]));
            if (d < 0.6) {
                if (freezeTicks > 0) { eDead[i] = true; combo++; score += 200 * (1 << (combo - 1)); hiScore = Math.max(hiScore, score); }
                else { lives--; deathTicks = 70; return; }
            }
        }
    }

    // ---------------- drawing ----------------
    protected void paintComponent(Graphics g0) {
        super.paintComponent(g0);
        Graphics2D g = (Graphics2D) g0;
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        g.setFont(new Font("Monospaced", Font.BOLD, 18));
        g.setColor(new Color(120, 255, 170)); g.drawString("SCORE " + score, 12, 28);
        g.setColor(new Color(150, 170, 255)); g.drawString("BEST " + hiScore, W * T / 2 - 50, 28);
        g.setColor(new Color(255, 210, 120)); g.drawString("LEVEL " + level, W * T - 110, 28);

        for (int y = 0; y < H; y++) for (int x = 0; x < W; x++) {
            int sx = x * T, sy = TOP + y * T; char c = grid[y][x];
            if (c == '#') {
                g.setColor(new Color(22, 40, 90)); g.fillRoundRect(sx + 2, sy + 2, T - 4, T - 4, 10, 10);
                g.setColor(new Color(70, 140, 255)); g.setStroke(new BasicStroke(2f)); g.drawRoundRect(sx + 3, sy + 3, T - 6, T - 6, 10, 10);
            } else if (c == '-') {
                g.setColor(new Color(120, 200, 255)); g.fillRect(sx, sy + T / 2 - 2, T, 4);
            } else if (c == '.') {
                g.setColor(new Color(255, 240, 170)); g.fillOval(sx + T / 2 - 3, sy + T / 2 - 3, 6, 6);
            } else if (c == '*') {
                double pulse = 1 + 0.15 * Math.sin(tick / 6.0);
                int r = (int) (8 * pulse); int cx = sx + T / 2, cy = sy + T / 2;
                Polygon p = new Polygon(new int[]{cx, cx + r, cx, cx - r}, new int[]{cy - r - 2, cy, cy + r + 2, cy}, 4);
                g.setColor(new Color(130, 230, 255)); g.fillPolygon(p);
                g.setColor(Color.WHITE); g.setStroke(new BasicStroke(1.5f)); g.drawPolygon(p);
            }
        }
        g.setStroke(new BasicStroke(1f));

        for (int i = 0; i < 4; i++) drawEnemy(g, i);
        if (!(deathTicks > 0 && deathTicks < 40 && (tick / 5) % 2 == 0) && !gameOver) drawPlayer(g);

        // lives
        for (int i = 0; i < lives; i++) drawBug(g, 20 + i * 30, TOP + H * T + 20, 9, 0, 0);
        g.setFont(new Font("SansSerif", Font.PLAIN, 13)); g.setColor(new Color(150, 160, 190));
        g.drawString("Arrows/WASD: move    P: pause", W * T - 230, TOP + H * T + 25);

        String msg = null, sub = null;
        if (gameOver) { msg = "GAME OVER"; sub = "Score " + score + "  ·  Press ENTER to play again"; }
        else if (paused) msg = "PAUSED";
        else if (levelClear) msg = "LEVEL CLEAR!";
        else if (readyTicks > 0) msg = level == 1 && score == 0 ? "READY!" : "LEVEL " + level;
        else if (!pMoving) msg = "PRESS AN ARROW KEY";
        if (msg != null) {
            if (gameOver || paused) { g.setColor(new Color(0, 0, 0, 160)); g.fillRect(0, TOP, W * T, H * T); }
            g.setFont(new Font("SansSerif", Font.BOLD, 34)); FontMetrics fm = g.getFontMetrics();
            g.setColor(new Color(255, 230, 120)); g.drawString(msg, (W * T - fm.stringWidth(msg)) / 2, TOP + 13 * T + 4);
            if (sub != null) { g.setFont(new Font("SansSerif", Font.PLAIN, 16)); fm = g.getFontMetrics(); g.setColor(Color.WHITE);
                g.drawString(sub, (W * T - fm.stringWidth(sub)) / 2, TOP + 14 * T + 10); }
        }
    }

    // the player: a round glowing bug-bot with antennae and big eyes
    void drawPlayer(Graphics2D g) {
        double x = fx(px, pdir, psub), y = fy(py, pdir, psub);
        if (x > W - 0.5) x -= W; if (x < -0.5) x += W;
        drawBug(g, (int) (x * T + T / 2), (int) (TOP + y * T + T / 2), 11, pdir, tick);
    }

    void drawBug(Graphics2D g, int cx, int cy, int r, int dir, int t) {
        g.setColor(new Color(120, 255, 170, 50)); g.fillOval(cx - r - 6, cy - r - 6, 2 * r + 12, 2 * r + 12); // glow
        g.setColor(new Color(90, 230, 140)); g.fillOval(cx - r, cy - r, 2 * r, 2 * r);
        g.setColor(new Color(40, 150, 90)); g.setStroke(new BasicStroke(1.5f)); g.drawOval(cx - r, cy - r, 2 * r, 2 * r);
        // antennae with bobbing tips
        int bob = (int) (2 * Math.sin(t / 4.0));
        g.setColor(new Color(40, 150, 90));
        g.drawLine(cx - 4, cy - r + 2, cx - 8, cy - r - 6 + bob); g.drawLine(cx + 4, cy - r + 2, cx + 8, cy - r - 6 - bob);
        g.setColor(new Color(255, 240, 120)); g.fillOval(cx - 11, cy - r - 9 + bob, 5, 5); g.fillOval(cx + 6, cy - r - 9 - bob, 5, 5);
        // eyes look in the movement direction
        int lx = DX[dir] * 2, ly = DY[dir] * 2;
        g.setColor(Color.WHITE); g.fillOval(cx - 7, cy - 4, 6, 7); g.fillOval(cx + 1, cy - 4, 6, 7);
        g.setColor(new Color(20, 30, 40)); g.fillOval(cx - 6 + lx, cy - 2 + ly, 3, 3); g.fillOval(cx + 2 + lx, cy - 2 + ly, 3, 3);
        g.setStroke(new BasicStroke(1f));
    }

    // enemies: spiky hovering drones with a single eye
    void drawEnemy(Graphics2D g, int i) {
        double x = fx(ex[i], edir[i], esub[i]), y = fy(ey[i], edir[i], esub[i]);
        int cx = (int) (x * T + T / 2), cy = (int) (TOP + y * T + T / 2);
        if (eDead[i]) { // shattered: just a drifting core spark
            g.setColor(new Color(200, 230, 255)); g.fillOval(cx - 4, cy - 4, 8, 8); return;
        }
        boolean frozen = freezeTicks > 0, blink = frozen && freezeTicks < 90 && (tick / 8) % 2 == 0;
        Color body = frozen ? (blink ? Color.WHITE : new Color(150, 210, 255)) : EN_COL[i];
        int spikes = 8, rOut = 12, rIn = 8;
        double rot = tick / 10.0 + i;
        Path2D star = new Path2D.Double();
        for (int s = 0; s < spikes * 2; s++) {
            double a = rot + Math.PI * s / spikes, rr = s % 2 == 0 ? rOut : rIn;
            double sx = cx + Math.cos(a) * rr, sy = cy + Math.sin(a) * rr;
            if (s == 0) star.moveTo(sx, sy); else star.lineTo(sx, sy);
        }
        star.closePath();
        g.setColor(body); g.fill(star);
        g.setColor(body.darker()); g.draw(star);
        g.setColor(frozen ? new Color(230, 245, 255) : new Color(30, 20, 40)); g.fillOval(cx - 6, cy - 6, 12, 12);
        g.setColor(frozen ? new Color(90, 150, 220) : Color.WHITE);
        int lx = frozen ? 0 : DX[edir[i]] * 2, ly = frozen ? 0 : DY[edir[i]] * 2;
        g.fillOval(cx - 3 + lx, cy - 3 + ly, 6, 6);
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            JFrame f = new JFrame("Glowbug Maze");
            GlowbugMaze gm = new GlowbugMaze();
            f.add(gm); f.pack(); f.setResizable(false);
            f.setLocationRelativeTo(null);
            f.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            f.setVisible(true);
            gm.requestFocusInWindow();
        });
    }
}
