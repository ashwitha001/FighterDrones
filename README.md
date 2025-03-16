# FighterDrones
**SYSC3303 A4 - Group 4**
> Iteration 3

This submission includes:
- A README file
- **"FighterDrones"** folder: Folder containing all source code, including:
    - `src/`: Main application code
    - `tests/`: Unit tests
- **"Sequence Diagram.png"**: UML sequence diagram
- **"Class Diagram.png"**: UML class diagram
- **"Iteration3SequenceDiagram.png"**: UML sequence diagram
- **"Iteration3ClassDiagram.png"**: UML class diagram
- **"Iteration3StateMachineDrone.png"**: UML sequence diagram
- **"Iteration3StateMachineScheduler.png"**: UML sequence diagram

## Team Members & Responsibilities
- **Quinn Vo**: Code base: Responsible for coding the scheduling behaviour of the drones
- **Ashwitha Ala**: Code base: Responsible for setting up UDP/RPC 
- **Michael Palummieri**: Code base: Responsible for revising Quinn and Ashwitha's work as needed, as well as for merging their work together into a working program, also finishing touches/polishing
- **Tudor Lungu**: Unit tests
- **Samson Ha**: Class Diagrams, Sequence Diagram, State Machine Diagrams
- **Christina Dang**: Unit tests

## Set up Instructions
- **To run code:**
  -
  1. Open FighterDrones folder in IntelliJ
  2. Run main.SchedulerMain
  3. Run main.DroneMain with a parameter: The file requires an integer parameter for the drone's ID. { Run this file for as many drones as you want to have}
  4. Run main.FireIncidentMain
  5. Observe the output
  
- **To run test:**
  -
    - Test files used:
    1. test.CoordinatesTest.java
    2. test.MessageTest.java
    3. test.FireIncidentSubsystemTest.java
    4. test.SchedulerTest.java
    5. test.LoggerTest.java
    6. test.DroneSubsystemTest.java
    7. test.UDPTest.java
    8. test.UtilityTest.java
