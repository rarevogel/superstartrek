package nl.rarevogel.startrek;

final class Instructions {
    static final String TEXT =
        "BEDIENING OP DE TELEFOON\n" +
        "------------------------\n" +
        "- TIK een commando (NAV, SRS ...) of typ het met het toetsenbord.\n" +
        "- KOERS: sleep in het kompas, typ een getal, of tik een sector\n" +
        "  in de scan. Tik in GALAXY RECORD een kwadrant om er naartoe\n" +
        "  te springen (de warpfactor wordt voor je ingevuld).\n" +
        "  Richten werkt alleen als de LIBRARY-COMPUTER heel is.\n" +
        "- Torpedo: eerste tik richt, tweede tik op hetzelfde doel vuurt.\n" +
        "- Knijp in de terminal om de letters groter of kleiner te maken.\n" +
        "- Het spel wordt bewaard bij elke COMMAND-prompt.\n" +
        "\n" +
        "      INSTRUCTIONS FOR 'SUPER STAR TREK'\n" +
        "\n" +
        "1. WHEN YOU SEE 'COMMAND ?' PRINTED, ENTER ONE OF THE LEGAL\n" +
        "     COMMANDS (NAV,SRS,LRS,PHA,TOR,SHE,DAM,COM, OR XXX).\n" +
        "2. IF YOU SHOULD TYPE IN AN ILLEGAL COMMAND, YOU'LL GET A SHORT\n" +
        "     LIST OF THE LEGAL COMMANDS PRINTED OUT.\n" +
        "3. SOME COMMANDS REQUIRE YOU TO ENTER DATA (FOR EXAMPLE, THE\n" +
        "     'NAV' COMMAND COMES BACK WITH 'COURSE (1-9) ?'.)  IF YOU\n" +
        "     TYPE IN ILLEGAL DATA (LIKE NEGATIVE NUMBERS), THAN COMMAND\n" +
        "     WILL BE ABORTED\n" +
        "\n" +
        "     THE GALAXY IS DIVIDED INTO AN 8 X 8 QUADRANT GRID,\n" +
        "AND EACH QUADRANT IS FURTHER DIVIDED INTO AN 8 X 8 SECTOR GRID.\n" +
        "\n" +
        "     YOU WILL BE ASSIGNED A STARTING POINT SOMEWHERE IN THE\n" +
        "GALAXY TO BEGIN A TOUR OF DUTY AS COMANDER OF THE STARSHIP\n" +
        "'ENTERPRISE'; YOUR MISSION: TO SEEK AND DESTROY THE FLEET OF\n" +
        "KLINGON WARWHIPS WHICH ARE MENACING THE UNITED FEDERATION OF\n" +
        "PLANETS.\n" +
        "\n" +
        "     YOU HAVE THE FOLLOWING COMMANDS AVAILABLE TO YOU AS CAPTAIN\n" +
        "OF THE STARSHIP ENTERPRISE:\n" +
        "\n" +
        "'NAV' COMMAND = WARP ENGINE CONTROL --\n" +
        "     COURSE IS IN A CIRCULAR NUMERICAL      4  3  2\n" +
        "     VECTOR ARRANGEMENT AS SHOWN             . . .\n" +
        "     INTEGER AND REAL VALUES MAY BE           ...\n" +
        "     USED.  (THUS COURSE 1.5 IS HALF-     5 ---*--- 1\n" +
        "     WAY BETWEEN 1 AND 2                      ...\n" +
        "                                             . . .\n" +
        "     VALUES MAY APPROACH 9.0, WHICH         6  7  8\n" +
        "     ITSELF IS EQUIVALENT TO 1.0\n" +
        "                                            COURSE\n" +
        "     ONE WARP FACTOR IS THE SIZE OF \n" +
        "     ONE QUADTANT.  THEREFORE, TO GET\n" +
        "     FROM QUADRANT 6,5 TO 5,5, YOU WOULD\n" +
        "     USE COURSE 3, WARP FACTOR 1.\n" +
        "\n" +
        "'SRS' COMMAND = SHORT RANGE SENSOR SCAN\n" +
        "     SHOWS YOU A SCAN OF YOUR PRESENT QUADRANT.\n" +
        "\n" +
        "     SYMBOLOGY ON YOUR SENSOR SCREEN IS AS FOLLOWS:\n" +
        "        <*> = YOUR STARSHIP'S POSITION\n" +
        "        +K+ = KLINGON BATTLE CRUISER\n" +
        "        >!< = FEDERATION STARBASE (REFUEL/REPAIR/RE-ARM HERE!)\n" +
        "         *  = STAR\n" +
        "\n" +
        "     A CONDENSED 'STATUS REPORT' WILL ALSO BE PRESENTED.\n" +
        "\n" +
        "'LRS' COMMAND = LONG RANGE SENSOR SCAN\n" +
        "     SHOWS CONDITIONS IN SPACE FOR ONE QUADRANT ON EACH SIDE\n" +
        "     OF THE ENTERPRISE (WHICH IS IN THE MIDDLE OF THE SCAN)\n" +
        "     THE SCAN IS CODED IN THE FORM '###', WHERE TH UNITS DIGIT\n" +
        "     IS THE NUMBER OF STARS, THE TENS DIGIT IS THE NUMBER OF\n" +
        "     STARBASES, AND THE HUNDRESDS DIGIT IS THE NUMBER OF\n" +
        "     KLINGONS.\n" +
        "\n" +
        "     EXAMPLE - 207 = 2 KLINGONS, NO STARBASES, & 7 STARS.\n" +
        "\n" +
        "'PHA' COMMAND = PHASER CONTROL.\n" +
        "     ALLOWS YOU TO DESTROY THE KLINGON BATTLE CRUISERS BY \n" +
        "     ZAPPING THEM WITH SUITABLY LARGE UNITS OF ENERGY TO\n" +
        "     DEPLETE THEIR SHIELD POWER.  (REMEMBER, KLINGONS HAVE\n" +
        "     PHASERS TOO!)\n" +
        "\n" +
        "'TOR' COMMAND = PHOTON TORPEDO CONTROL\n" +
        "     TORPEDO COURSE IS THE SAME AS USED IN WARP ENGINE CONTROL\n" +
        "     IF YOU HIT THE KLINGON VESSEL, HE IS DESTROYED AND\n" +
        "     CANNOT FIRE BACK AT YOU.  IF YOU MISS, YOU ARE SUBJECT TO\n" +
        "     HIS PHASER FIRE.  IN EITHER CASE, YOU ARE ALSO SUBJECT TO \n" +
        "     THE PHASER FIRE OF ALL OTHER KLINGONS IN THE QUADRANT.\n" +
        "\n" +
        "     THE LIBRARY-COMPUTER ('COM' COMMAND) HAS AN OPTION TO \n" +
        "     COMPUTE TORPEDO TRAJECTORY FOR YOU (OPTION 2)\n" +
        "\n" +
        "'SHE' COMMAND = SHIELD CONTROL\n" +
        "     DEFINES THE NUMBER OF ENERGY UNITS TO BE ASSIGNED TO THE\n" +
        "     SHIELDS.  ENERGY IS TAKEN FROM TOTAL SHIP'S ENERGY.  NOTE\n" +
        "     THAN THE STATUS DISPLAY TOTAL ENERGY INCLUDES SHIELD ENERGY\n" +
        "\n" +
        "'DAM' COMMAND = DAMMAGE CONTROL REPORT\n" +
        "     GIVES THE STATE OF REPAIR OF ALL DEVICES.  WHERE A NEGATIVE\n" +
        "     'STATE OF REPAIR' SHOWS THAT THE DEVICE IS TEMPORARILY\n" +
        "     DAMAGED.\n" +
        "\n" +
        "'COM' COMMAND = LIBRARY-COMPUTER\n" +
        "     THE LIBRARY-COMPUTER CONTAINS SIX OPTIONS:\n" +
        "     OPTION 0 = CUMULATIVE GALACTIC RECORD\n" +
        "        THIS OPTION SHOWES COMPUTER MEMORY OF THE RESULTS OF ALL\n" +
        "        PREVIOUS SHORT AND LONG RANGE SENSOR SCANS\n" +
        "     OPTION 1 = STATUS REPORT\n" +
        "        THIS OPTION SHOWS THE NUMBER OF KLINGONS, STARDATES,\n" +
        "        AND STARBASES REMAINING IN THE GAME.\n" +
        "     OPTION 2 = PHOTON TORPEDO DATA\n" +
        "        WHICH GIVES DIRECTIONS AND DISTANCE FROM THE ENTERPRISE\n" +
        "        TO ALL KLINGONS IN YOUR QUADRANT\n" +
        "     OPTION 3 = STARBASE NAV DATA\n" +
        "        THIS OPTION GIVES DIRECTION AND DISTANCE TO ANY \n" +
        "        STARBASE WITHIN YOUR QUADRANT\n" +
        "     OPTION 4 = DIRECTION/DISTANCE CALCULATOR\n" +
        "        THIS OPTION ALLOWS YOU TO ENTER COORDINATES FOR\n" +
        "        DIRECTION/DISTANCE CALCULATIONS\n" +
        "     OPTION 5 = GALACTIC /REGION NAME/ MAP\n" +
        "        THIS OPTION PRINTS THE NAMES OF THE SIXTEEN MAJOR \n" +
        "        GALACTIC REGIONS REFERRED TO IN THE GAME.\n" +
        "";
}
