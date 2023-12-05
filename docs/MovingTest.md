
The robot must move trying to explore the environment towards unknown areas

```mermaid

graph TD;
    start([start/Halt])
    target[target/SelectTarget]
    move[move/Move]
    
    start--timeout-->target;
    start--*block-->avoid;

    target--completed-->move;
    target--*block-->avoid;
    
    avoid--completed-->start
    
    move--completed-->start
    move--*block-->avoid

```