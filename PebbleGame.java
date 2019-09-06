import java.util.Random;
import java.util.Scanner;
import java.io.*;
import java.util.concurrent.*;
import java.lang.*;
/*
Controls entire game
*/
class PebbleGame {

    public BagManager myBagManager = new BagManager();

    BufferedReader input = new BufferedReader(new InputStreamReader(System.in));
    Scanner keyBScan = new Scanner(System.in);
    Scanner fileScan;

    Random random = new Random();

    public int numberOfPlayers;

    Player[] players;
    Thread[] threads;

    public static void main(String[] args) {

        PebbleGame game = new PebbleGame();
        game.startGame();

    }

    /*
    Takes an integer array named 'array' and a starting index named 'start'
    Returns the index of the first 0 in array after start or -1 if there are
    no zeros
    */
    int findFirstEmpty(int[] array, int start) {
        for (int i=start; i<array.length;i++) {
            if (array[i] == 0) {
                return i;
            }
        }
        return -1;
    }

    int[] loadfile(int bagNum) {
        int[] pebbleArray = new int[]{1,2,3,4,5,6,7,8,9,10,11};
        boolean searching = true;
        while (searching) {
            try {
                System.out.println("Please enter a valid filename for bag " + bagNum + ": ");
                String filename = keyBScan.next();
                File file = new File(filename);
                fileScan = new Scanner(file);
                String[] fileSplit = fileScan.next().split(",");
                pebbleArray = new int[fileSplit.length];
                for (int i=0;i<fileSplit.length;i++) {
                    pebbleArray[i] = Integer.parseInt(fileSplit[i]);
                }
            }
            catch (Exception e) {} // to find specific expception if possible

            // Validation
            if(pebbleArray.length >= numberOfPlayers * 11){
                // Check if the length of the bags is adequate for the number of players
                searching = false;
            } else{System.out.println("Bag doesn't contain enough pebbles");}

            for(int i = 0; i < pebbleArray.length; i++){
                if(pebbleArray[i] <= 0){
                    searching = true;
                    System.out.println("Bag contains negative weight pebbles.");
                }
            }
        }
        return pebbleArray;
    }

    public void notify(int playerID) {
        players[playerID].notify();
    }

    void startGame() {
        boolean searching = true;
        while (searching) {
            try {
                System.out.println("Please input a valid number of players: ");
                numberOfPlayers = Integer.parseInt(keyBScan.next());
                searching = false;
            }
            catch (NumberFormatException e) {
            }
        }

        // Get list of ints from files
        int[] pebbleArray1 = loadfile(1);
        int[] pebbleArray2 = loadfile(2);
        int[] pebbleArray3 = loadfile(3);

        myBagManager.setBagManager(numberOfPlayers, pebbleArray1, pebbleArray2, pebbleArray3);

        /*List of player objects in order to reference the next player in the sequence
        from the previous one. List of threads in order to use thread methods on specific threads*/

        players = new Player[numberOfPlayers];
        threads = new Thread[numberOfPlayers];
        for (int i = 0; i < numberOfPlayers; i++) {
            players[i] = new Player(this, i);

            if(i > 0) {
                players[i - 1].nextPlayer = players[i]; // setting a reference to the next Player instance for the previous player created
            }

            threads[i] = new Thread(players[i]);
            threads[i].start();
            Thread.yield();
        }

        players[0].nextPlayer = players[1];
        players[numberOfPlayers - 1].nextPlayer = players[0];

        synchronized(players[0]) {
            players[0].notify();
        }

    }

    public void stopGame() {
        for (int i=0;i<players.length;i++) {
            synchronized(players[i]){
                threads[i].interrupt();
            }
        }
    }


    /*
    Player class; simulates a player in the game
    */
    class Player implements Runnable {

        public int myID;
        int index;
        int[] myPebbles;

        public Player nextPlayer;
        File myFile;
        BufferedWriter myWriter;
        PebbleGame myGame;

        /*
        Constructor
        */
        public Player(PebbleGame game, int playerID) {
            myID = playerID;
            myPebbles = new int[10];
            myGame = game;
            myFile = new File("player" + myID + "_output.txt");
            try {
                myWriter = new BufferedWriter(new FileWriter(myFile,false));
                myWriter.close();
            } catch(IOException e) {
                System.out.println(e);
            }
        }

        /*
        Player thread main loop
        */
        public void run() {
            synchronized(this) {
                while (!Thread.currentThread().isInterrupted()) {
                    try {
                        wait();
                    }
                    catch (InterruptedException e) {
                        return;
                    }

                    /*
                    Find each empty space in myPebbles and fill it with a random
                    pebble from the same random bag
                    */
                    index = 0;
                    int pebble = 0;
                    int bagChoice = 0;
                    do {
                        index = findFirstEmpty(myPebbles,index);
                        if (index > -1) {
                            do {
                                bagChoice = random.nextInt(3);
                                pebble = myGame.myBagManager.getRPebbleFromRBag(bagChoice, myID);
                            } while (pebble == 0);
                            myPebbles[index] = pebble;

                            output(true, myPebbles[index], bagChoice);
                        }
                    } while (index > -1);

                    if (sumWeights() != 100) {
                        index = random.nextInt(myPebbles.length);
                        int weight = myPebbles[index];
                        myPebbles[index] = 0;

                        myGame.myBagManager.updateWhiteBag(myID, weight);
                        output(false, weight, bagChoice);
                        synchronized(nextPlayer) {
                            nextPlayer.notify();
                        }

                    }
                    else
                    {

                        /*
                        Stop the game if this player won
                        */
                        System.out.println("Player" + myID + " won!");
                        myGame.stopGame();
                    }
                }
            }
        }

        /*
        Return the sum of the weights of all pebbles held by this player
        */
        int sumWeights()
        {
            int total = 0;
            for (int i=0;i<myPebbles.length; i++)
            {
                total += myPebbles[i];
            }
            return total;
        }

        /*
        Prints player's last action and current inventory to the console and a
        text file
        */
        void output(boolean drawn, int weight, int bag)
        {
            String message = "";
            char bagName;
            if (drawn)
            {
                switch(bag) {
                    case 0: bagName = 'X'; break;
                    case 1: bagName = 'Y'; break;
                    default: bagName = 'Z'; break;
                }
                message += "player" + myID + " has drawn a " + weight + " pebble from bag " + bagName;

            } else
            {
                switch(bag) {
                    case 0: bagName = 'A'; break;
                    case 1: bagName = 'B'; break;
                    default: bagName = 'C'; break;
                }
                message += "player" + myID + " has discarded a " + weight + " pebble to bag " + bagName;
            }

            message += "\nplayer" + myID + " hand is ";
            for (int i=0;i<myPebbles.length;i++)
            {
                if (myPebbles[i] != 0) {
                    message += myPebbles[i] + ", ";
                }
            }
            message = message.substring(0, message.length() - 2);
            message+="\n";

            System.out.println(message);
            try {
                myWriter = new BufferedWriter(new FileWriter(myFile,true));
                myWriter.write(message);
                myWriter.close();
            } catch(IOException e) {
                System.out.println(e);
            }
        }

    }

    /*
    A BagManager controls the three black bags used in the game
    All interaction between players and bags goes through this class
    */
    class BagManager {
        BlackBag BagX = new BlackBag();
        BlackBag BagY = new BlackBag();
        BlackBag BagZ = new BlackBag();
        BlackBag[] playerBagLink;
        BlackBag[] Bags = new BlackBag[]{BagX, BagY, BagZ} ;

        public void setBagManager(int numberOfPlayers, int[] bag1, int[] bag2, int[] bag3) {
            BagX.setPebbles(bag1);
            BagY.setPebbles(bag2);
            BagZ.setPebbles(bag3);
            playerBagLink = new BlackBag[numberOfPlayers];
        }

        public synchronized int getRPebbleFromRBag(int bagChoice, int playerID) {
            playerBagLink[playerID] = Bags[bagChoice];
            return playerBagLink[playerID].getRandomPebble();
        }

        public synchronized void updateWhiteBag(int playerID, int pebble ){
            playerBagLink[playerID].addPebbleToWhiteBag(pebble);
        }


        /*
        Nested class which represents a black bag (and corresponding white
        bag) in the game
        */
        class BlackBag {

            char bagName;
            char whiteBagName;

            int[] whiteBag;
            int[] myPebbles;
            int randomIndex;

            /*
            This function sets the contents of a black bag to the array
            passed to it and sets the corresponding white bag to an empty
            array of the correct length
            */
            void setPebbles(int[] newPebbles) {
                myPebbles = newPebbles;
                whiteBag = new int[myPebbles.length];
            }

            /*
            Returns a random pebble from the bag, removes it and refills if
            the bag was emptied
            */
            public int getRandomPebble() {
                if (!isEmpty()) {
                    /*
                    Try to find an array index which is not 0
                    */
                    do {
                        randomIndex = random.nextInt(myPebbles.length);
                    } while (myPebbles[randomIndex] == 0);

                    // Save chosen pebble and set its old index to 0
                    int weight = myPebbles[randomIndex];
                    myPebbles[randomIndex] = 0;

                    /*
                    If the bag is now empty refill from the white bag
                    The SetPebbles function automatically empties the white
                    bag
                    */
                    if (isEmpty()) {
                        setPebbles(whiteBag);
                    }

                    return weight;
                }

                /*
                In the event that the bag is already empty try to refill
                from white bag and return 0 (no pebble)
                */
                setPebbles(whiteBag);
                return 0;
            }

            /*
            Check if bag contains any pebbles
            */
            boolean isEmpty() {
                for (int i=0;i<myPebbles.length;i++) {
                    /*
                    If any index of the array is non-zero the array is not
                    empty so return false
                    */
                    if (myPebbles[i] != 0) {
                        return false;
                    }
                }
                return true;
            }

            /*
            Adds a pebble to this black bag's corresponding white bag
            */
            void addPebbleToWhiteBag(int pebble) {
                whiteBag[findFirstEmpty(whiteBag,0)] = pebble;
            }

        }
    }
}