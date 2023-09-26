package COVIDSweeper;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Random;
import java.util.Scanner;

class Coords implements Comparable<Coords> {
    public int x;
    public int y;

    Coords(int x, int y) {
        this.x = x;
        this.y = y;
    }

    @Override
    public int compareTo(Coords o) {
        return Integer.compare(this.x + this.y, o.x + o.y);
    }

    @Override
    public String toString() {
        return "Coords{" + "x=" + x + ", y=" + y + '}';
    }
}

class Score implements Comparable<Score> { //A quick score data structure.
    String name;
    int score;

    Score (String name, int score) {
        this.name = name;
        this.score = score;
    }

    @Override
    public int compareTo(Score o) {
        if (this == o) {
            return 0;
        } else if (this.score == o.score) { //If they're the same score, compare the names.
            return this.name.compareTo(o.name);
        } else if (this.score > o.score) {
            return 1;
        } else {
            return -1;
        }
    }
}

class FileHandler {
    File file;
    Scanner scanner;
    FileWriter quil;

    public ArrayList<String> readingStringiness(String filename) throws FileNotFoundException {
        // Whatever uses FileHandler will have to account for I/O exceptions.
        ArrayList<String> lines = new ArrayList<>();
        file = new File(filename);
        scanner = new Scanner(file);
        while (scanner.hasNextLine()) {
            lines.add(scanner.nextLine()); //Read into String Array.
        }

        return lines;
    }

    public void writingFile(String filename, String data) throws IOException {
        file = new File(filename);
        quil = new FileWriter(file);
        quil.write(data + "\n"); //This one replaces the text file with a near-identical copy.
        quil.close();
    }

    public void writingScoreFile(String filename, ArrayList<Score> data) throws IOException {
        writingFile(filename, "Scores\n======");
        file = new File(filename);
        quil = new FileWriter(file, true);
        for (Score i : data)
            quil.write(i.name + "=" + i.score + "\n");
        quil.close();
    }
}

abstract class Square extends JPanel {
    private boolean uncovered = false;
    protected int type = 0; // 0 for Square, 1 for Cell, 2 for 'Bomb'
    private boolean flagged = false;
    private int surrounding_mines = 0;

    JLabel Text;
    String desired_text;
    Coords relative_place;

    Square(int row, int col) {
        relative_place = new Coords(col, row);
        desired_text = "";
        setBackground(new Color(171, 175, 172));
        setBorder(BorderFactory.createLineBorder(new Color(97, 110, 127)));
        setPreferredSize(new Dimension(25, 25));
        Text = new JLabel(desired_text);
        add(Text);
        setVisible(true);
    }

    public boolean isUncovered() {
        return uncovered;
    }

    public boolean isFlagged() {
        return flagged;
    }

    public boolean isSurrounded() {
        return (surrounding_mines > 0);
    }

    public void changeColour(Color bg) {
        setBackground(bg);
    }
    public void setSurroundingMines(int amount) {
        surrounding_mines = amount;
    }

    public int getSurrounding_mines() {
        return surrounding_mines;
    }


    public abstract void uncover();

    public void setUncovered(boolean value) {
        this.uncovered = value;
    }

    public int getType() {
        return type;
    }

    public void toggleFlag() {
        if(!isUncovered()) {
            if (flagged) {
                flagged = false;
                setBackground(new Color(171, 175, 172));
            } else {
                flagged = true;
                setBackground(new Color(236, 158, 158));

            }
        }
    }

}

class Mine extends Square {
    Mine(int row, int col) {
        super(row, col);
        this.type = 2;
        this.desired_text = "\uD83D\uDCA3";
    }

    @Override
    public void uncover() {
        if (!this.isFlagged()) {
            this.setUncovered(true);
            Text.setText(desired_text);
        }
        setBackground(new Color(0xE39056));
    }

}

class Cell extends Square {

    Cell(int row, int col) {
        super(row, col);
        this.type = 1;
        this.desired_text = String.valueOf(this.getSurrounding_mines());
    }

    @Override
    public void uncover() {
        setBackground(new Color(0xC2DEFF));
        desired_text = String.valueOf(this.getSurrounding_mines());
        if (this.getSurrounding_mines() == 0) {
            this.desired_text = "";
        }
        if (!this.isFlagged()) {
            this.setUncovered(true);
            Text.setText(desired_text);
        }

    }
}

class Game extends JFrame {
    private int MINES;
    private int active_mines;
    public int current_score = 0;
    private ArrayList<Coords> mine_coordinates; // To easily know where all our mines are in each game.
    private double health = 0; // Or lack thereof.
    boolean game_running = false; //You can only interact with the minesweeper grid if you haven't lost/won.
    boolean victory;
    private boolean first_turn;

    JPanel minesweeper_panel; // panel used to group together and treat the cells as one..
    Square[][] minefield; // The array containing all of the individual cells.
    JLabel score_label;
    JLabel health_label;
    ScoreWindow scoreboard;

    public Game() {
        // call initialise

        initialise(16,16,35);


        // key listener to respond to key events
        addKeyListener(new KeyboardListener(this));


        // standard configuration
        setSize(new Dimension(500, 600));
        getContentPane().setBackground(new Color(0x555555));
        setTitle("1903148 - COVIDSweepr");
        setResizable(true);
        setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        setVisible(true);
    }

    void startScoreboard() {
        String player_name = JOptionPane.showInputDialog("What is your name?");
        if (player_name.length() < 1) {player_name = "Anonymous";} //Just in case they're too lazy to put in a name.
        try {
            this.scoreboard = new ScoreWindow(new Score(player_name, this.current_score), this.victory);
        } catch (IOException ioException) {
            ioException.printStackTrace();
        }
    }

    void initialise(int rows, int cols, int mines) {
        MINES = mines; //Unchanging max mines
        active_mines = MINES; //All mines still 'active' (not triggered)
        current_score = 0;
        health = 0;
        first_turn = true;
        // if there was a previous finished/unfinished game, reset to original state.
        if(minesweeper_panel != null) {
            this.remove(minesweeper_panel);
            this.remove(score_label);
            this.remove(health_label);
            health_label = null;
            minesweeper_panel = null;
            score_label = null;
        }

        minesweeper_panel = new JPanel(new GridLayout(rows, cols));
        minesweeper_panel.setBackground(new Color(0x555555));

        minefield = new Square[rows][cols];

        score_label = new JLabel("Score: " + current_score, JLabel.LEFT);
        score_label.setForeground(Color.white);
        score_label.setFont(new Font("Arial", Font.BOLD, 18));

        health_label = new JLabel("Infection Chance: 0%", JLabel.RIGHT);
        health_label.setFont(new Font("Arial", Font.ITALIC, 18));
        health_label.setForeground(Color.orange);

        populate(); //Put the bombs in, then place cells where bombs aren't.

        for (int i = 0; i < minefield.length; i++) {
            for (int j = 0; j < minefield[0].length; j++) {
                minefield[i][j].setSurroundingMines(countMines(i, j));
            }
        }


        // add panels to frame (via BorderLayout)
        this.add(health_label, BorderLayout.NORTH);
        this.add(minesweeper_panel, BorderLayout.CENTER);
        this.add(score_label, BorderLayout.SOUTH);
        game_running = true;
    }

    public void game_over(boolean victory) {
        for (Coords i : mine_coordinates) {
            minefield[i.y][i.x].uncover();
            if (minefield[i.y][i.x].isFlagged()) { //If they got them all right.
                minefield[i.y][i.x].changeColour(new Color(0x8BD758));
            }
        }
        health_label.setText("You're back home...");
        if (victory) {
            System.out.println("PLAYER: Victory!");
        }
        game_running = false;
    }

    public void victory_check() {
        int flagged_mines = 0;
        for (Coords i : mine_coordinates) {
            if (minefield[i.y][i.x].isFlagged() && !minefield[i.y][i.x].isUncovered()) { //Whatever mines were flagged and not triggered.
                ++flagged_mines;
            }
        }
        if (flagged_mines == active_mines) { //So long as the player has gotten all non-triggered mines, they can still win.
            double percentage = (health / 6) * 100;
            Random rand = new Random();
            double chance = 100 * rand.nextDouble(); //If the random number is larger than whatever percentage you're at.
            if (chance > percentage) {
                victory = true;
                game_over(true); //You aren't infected... this time.
            } else {
                victory = false;
                game_over(false);
            }
        }
    }

    public void get_hurt() {
        if (health >= 6) { //The player has 6+1 chances when they get hit by bombs
            active_mines -= 1;
            health_label.setText("Infection Chance: CERTAIN.");
            health_label.setForeground(Color.red);
            victory = false;
            game_over(false); //The game is already over, and the player lost.
        } else {
            active_mines -=1;
            health += 1;
            health_label.setText("Infection Chance: " + health / 6 * 100 + "%"); // Chance of getting infected abstracted with this lives system.
        }
    }

    public int countMines(int x, int y) {
        int count = 0;
        for(int i = -1; i <= 1; i++) {
            if((x + i < 0) || (x + i >= minefield.length)) {
                continue;
            }
            for(int j = -1; j <= 1; j++) {
                if((y + j < 0) || (y + j >= minefield[0].length)) {
                    continue;
                }
                if(minefield[x + i][y + j].getType() == 2) {
                    count++;
                }
            }
        }
        return count;
    }

    public void populate() {
        Random rand = new Random();
        mine_coordinates = new ArrayList<>(); //Place the mines first
        int i = 0;
        while (i < MINES) {
            int cols = rand.nextInt(minefield[0].length);
            int rows = rand.nextInt(minefield.length);
            if (minefield[rows][cols] == null) {
                minefield[rows][cols] = new Mine(rows, cols);
                minefield[rows][cols].addMouseListener(new MouseClickListener(this));
                minesweeper_panel.add(minefield[rows][cols]);
                mine_coordinates.add(new Coords(cols, rows));
                i++;
            }
        }

        for(int row = 0; row < minefield.length; row++) {
            for(int col = 0; col < minefield[row].length; col++) {
                if (minefield[row][col] == null) { //For whatever still isn't initialized we put a Cell
                    minefield[row][col] = new Cell(row, col);
                    // unique mouse listener per panel to determine which panel was clicked
                    minefield[row][col].addMouseListener(new MouseClickListener(this));
                }
            }
        }

        for (Square[] squares : minefield) {
            for (int y = 0; y < minefield[0].length; y++) {
                minesweeper_panel.add(squares[y]);
            }
        }
    }

    public void reveal (int row, int col) { // Cascading reveals to make clicking blank spots reasonable.
        minefield[col][row].uncover();
        for(int i = -1; i <= 1; i++) {
            if((col + i < 0) || (col + i >= minefield.length)) {
                continue;
            }
            for(int j = -1; j <= 1; j++) {
                if((row + j < 0) || (row + j >= minefield[0].length)) {
                    continue;
                }
                if(minefield[col + i][row + j].getType() == 1 && !minefield[col + i][row + j].isFlagged() && !minefield[col + i][row + j].isSurrounded()) {
                    minefield[col + i][row + j].uncover();
                }
                if (first_turn && minefield[col + i][row + j].getType() == 1) { //The player's first turn gets clairvoyance
                    minefield[col + i][row + j].uncover();
                }
            }
        }
        first_turn = false;
    }

}

class ScoreWindow extends JFrame {
    Score latest_score;
    FileHandler files;
    ArrayList<Score> scores;
    JLabel verdict;
    JLabel latest_score_label;
    JLabel score_list_label;

    ScoreWindow(Score latest_score, boolean victory) throws IOException {
        scores = new ArrayList<>();
        scores.add(latest_score);
        this.latest_score = latest_score;
        setLayout(new GridLayout(4,1));
        readScores();
        sortScores();
        writeScores();
        initialize(victory);
        setLayout(new FlowLayout());
        setSize(new Dimension(500,600));
        setResizable(false);
        setTitle("1903148 - COVIDQuest - Results");
        getContentPane().setBackground(new Color(0x555555));
        setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        setVisible(true);
    }

    public void initialize(boolean victory) {
        if (this.verdict != null) {
            remove(verdict);
            remove(latest_score_label);
            remove(score_list_label);
            verdict = null;
            latest_score = null;
            score_list_label = null;
        }

        if (victory) {
            verdict = new JLabel("You're Safe.");
            verdict.setForeground(Color.green);
            verdict.setBackground(Color.WHITE);
        } else {
            verdict = new JLabel("You've contracted COVID-19");
            verdict.setForeground(Color.red);
            verdict.setBackground(new Color(0xE38948));
        }
        verdict.setFont(new Font("Helvetica", Font.BOLD, 32));
        add(verdict);

        latest_score_label = new JLabel(latest_score.name + ", you scored " + latest_score.score + " points total.");
        latest_score_label.setFont(new Font("Arial", Font.ITALIC, 25));
        latest_score_label.setBackground(new Color(0x898989));
        latest_score_label.setForeground(new Color(0xFFFFFF));
        add(latest_score_label);

        score_list_label = new JLabel();
        StringBuilder single_string = new StringBuilder();
        single_string.append("<html>TOP 5 HIGHEST SCORES<br>"); //HTML tags for line breaks in JLabel.
        try {
            for (int i = 0; i < 5; ++i) {
                single_string.append(i+1).append(". ").append(scores.get(i).name).append(" = ").append(scores.get(i).score).append(".<br>");
            }
        } catch (IndexOutOfBoundsException e) { //If we have less than five names.
            single_string.append("...And no one else...");
        }
        single_string.append("</html>");
        score_list_label.setText(single_string.toString());
        score_list_label.setForeground(new Color(0xC6C6C6));
        score_list_label.setFont(new Font("Arial", Font.BOLD, 25));
        score_list_label.setHorizontalAlignment(JLabel.CENTER);
        add(score_list_label);
    }


    public void readScores() throws IOException {
        files = new FileHandler();
        try {
            ArrayList<String> list = files.readingStringiness("scores.txt");
            for (int i = 0; i < list.size(); ++i) {
                if (i > 1) {
                    String[] split_entry = list.get(i).split("=");
                    scores.add(new Score(split_entry[0], Integer.parseInt(split_entry[1].strip())));
                }
            }
        } catch (FileNotFoundException e) {
            files.writingFile("scores.txt", "Scores\n======\n" + latest_score.name + "=" + latest_score.score);
        } catch (ArrayIndexOutOfBoundsException ignored) {
        }
    }

    public void writeScores() throws IOException {
        files = new FileHandler();
        files.writingScoreFile("scores.txt", scores);
    }

    public void sortScores() { //Sorts scores into descending order.
        scores.sort(Collections.reverseOrder());
    }
}

class MouseClickListener implements MouseListener {

    private final Game game; // game passed through to allow for game manipulation

    public MouseClickListener(Game game) {
        this.game = game;
    }

    @Override
    public void mouseClicked(MouseEvent e) {
        if (game.game_running) { //If the game is running, we use mouseClicked to reveal cells.
            Square component = (Square) e.getComponent();
            if (e.getButton() == 1) { //If LMB clicked.
                if (!component.isUncovered() && !component.isFlagged()) {
                    if (component.getType() == 2) { //If it's a bomb
                        component.uncover();
                        game.current_score -= 5;
                        game.score_label.setText("Score: " + game.current_score);
                        game.get_hurt();
                    } else { // If it isn't (A cell).
                        game.reveal(component.relative_place.x, component.relative_place.y); //Use the place of the cell in the array.
                        game.current_score += 10;
                        game.score_label.setText("Score: " + game.current_score);
                    }
                }
                game.victory_check();
            } else if (e.getButton() == 3) { //If RMB clicked
                if (!component.isUncovered()) {
                    component.toggleFlag();
                }
                game.victory_check();
            }
        } else { //If the game isn't running, then it would be time to bring up results.
            game.startScoreboard();
        }
    }

    @Override
    public void mousePressed(MouseEvent e) {

    }

    @Override
    public void mouseReleased(MouseEvent e) {

    }

    @Override
    public void mouseEntered(MouseEvent e) {

    }

    @Override
    public void mouseExited(MouseEvent e) {

    }
}

class KeyboardListener implements KeyListener {

    private final Game game; // game passed through to allow for game manipulation

    public KeyboardListener(Game game) {
        this.game = game;
    }

    @Override
    public void keyTyped(KeyEvent e) {

    }

    @Override
    public void keyPressed(KeyEvent e) {
        //                this.game.repaint();
        switch (e.getKeyCode()) {
            case KeyEvent.VK_UP -> System.out.println("Up");
            case KeyEvent.VK_DOWN -> System.out.println("Down");
            case KeyEvent.VK_LEFT -> System.out.println("Left");
            case KeyEvent.VK_RIGHT -> System.out.println("Right");
            case KeyEvent.VK_SPACE -> {
                System.out.println("SPACEBAR: Reset Game");
                this.game.initialise(16, 16, 128);
                this.game.revalidate(); // repaints node children, rather than node
            }
            default -> System.out.println("Other");
        }
    }

    @Override
    public void keyReleased(KeyEvent e) {

    }
}

public class main {
    public static void main(String[] args) {
        new Game();
    }
}
