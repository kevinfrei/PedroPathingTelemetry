package com.pedropathing.telemetry;

import com.qualcomm.robotcore.eventloop.opmode.OpMode;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;

public abstract class SelectableOpMode extends OpMode {
    private static OpMode previouslySelectedOpMode = null;
    private final Selector<Supplier<OpMode>> selector;
    private OpMode selectedOpMode;
    private final static String[] MESSAGE = {
            "Use the d-pad to move the cursor.",
            "Press right bumper or d-pad right to select.",
            "Press left bumper or d-pad left to go back."
    };
    private static final String LAST_LINE = "Or just press start to run %s";
    private static final String[] PREV_MESSAGE = {
        MESSAGE[0],
        MESSAGE[1],
        MESSAGE[2],
        "",
        "", // A placeholder for the LAST_LINE message
    };

    protected void beginOpMode(OpMode om) {
        onSelect();
        previouslySelectedOpMode = om;
        selectedOpMode = om;
        selectedOpMode.gamepad1 = gamepad1;
        selectedOpMode.gamepad2 = gamepad2;
        selectedOpMode.telemetry = telemetry;
        selectedOpMode.hardwareMap = hardwareMap;

        // why does the sdk have to suck so much
        final Field internalOpModeServices;
        try {
            internalOpModeServices = Objects.requireNonNull(OpMode.class.getSuperclass()).getDeclaredField("internalOpModeServices");
        } catch (NoSuchFieldException e) {
            throw new RuntimeException(e);
        }
        internalOpModeServices.setAccessible(true);
        try {
            internalOpModeServices.set(selectedOpMode, internalOpModeServices.get(this));
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }
        selectedOpMode.init();
    }

    public SelectableOpMode(String name, Consumer<SelectScope<Supplier<OpMode>>> opModes) {
        if (previouslySelectedOpMode != null) {
            PREV_MESSAGE[4] = String.format(LAST_LINE, previouslySelectedOpMode.getClass().getSimpleName());
            selector = Selector.create(name, opModes, PREV_MESSAGE);
        } else {
            selector = Selector.create(name, opModes, MESSAGE);
        }
        selector.onSelect(opModeSupplier -> beginOpMode(opModeSupplier.get()));
    }

    protected void onSelect() {
    }

    protected void onLog(List<String> line) {
    }

    @Override
    public final void init() {
    }

    @Override
    public final void init_loop() {
        if (selectedOpMode == null) {
            if (gamepad1.dpadUpWasPressed() || gamepad2.dpadUpWasPressed())
                selector.decrementSelected();
            else if (gamepad1.dpadDownWasPressed() || gamepad2.dpadDownWasPressed())
                selector.incrementSelected();
            else if (gamepad1.rightBumperWasPressed() ||
                    gamepad2.rightBumperWasPressed() ||
                    gamepad1.dpadRightWasPressed() ||
                    gamepad2.dpadRightWasPressed())
                selector.select();
            else if (gamepad1.leftBumperWasPressed() ||
                    gamepad2.leftBumperWasPressed() ||
                    gamepad1.dpadLeftWasPressed() ||
                    gamepad2.dpadLeftWasPressed())
                selector.goBack();

            List<String> lines = selector.getLines();
            for (String line : lines) {
                telemetry.addLine(line);
            }
            onLog(lines);
        } else if (
            gamepad1.leftBumperWasPressed() ||
            gamepad2.leftBumperWasPressed() ||
            gamepad1.dpadLeftWasPressed() ||
            gamepad2.dpadLeftWasPressed()
        ) {
            // Allow us to back up one, if we accidentally selected an opmode
            selector.goBack();
            selectedOpMode = null;
        } else selectedOpMode.init_loop();
    }

    protected void startPreviousOpMode() {
        beginOpMode(previouslySelectedOpMode);
        previouslySelectedOpMode.init();
        previouslySelectedOpMode.init_loop();
        previouslySelectedOpMode.init_loop();
        previouslySelectedOpMode.start();
    }

    @Override
    public final void start() {
        if (selectedOpMode == null) startPreviousOpMode();
        else selectedOpMode.start();
    }

    @Override
    public final void loop() {
        if (selectedOpMode == null) {
            telemetry.addLine("You forgot to select an opmode from the telemetry display. Oops.");
            telemetry.update();
        } else
            selectedOpMode.loop();
    }

    @Override
    public final void stop() {
        if (selectedOpMode != null) selectedOpMode.stop();
    }
}
