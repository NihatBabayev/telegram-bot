package com.example.telegrambot;

import com.example.telegrambot.entity.Task;
import com.example.telegrambot.repository.TaskRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;



import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

@Component
@RequiredArgsConstructor
public class Bot extends TelegramLongPollingBot {
    @Value(value = "${botToken}")
    private String botToken;
    @Value(value = "${botUsername}")
    private String botUsername;
    private boolean isEnteringTask = false;
    private final Map<Long, ScheduledFuture<?>> userSchedulers = new HashMap<>();


    @Autowired
    private final TaskRepository taskRepository;



    @Override
    public String getBotUsername() {
        return botUsername;
    }

    @Override
    public String getBotToken() {
        return botToken;
    }

    @Override
    public void onUpdateReceived(Update update) {
        var msg = update.getMessage();
        var user = msg.getFrom();
        var id = user.getId();
        var txt = msg.getText();

        if (msg.isCommand()) {
            if (txt.equals("/newtask")) {
                sendText(id, "Enter task description and time in the format: HH:mm - Task description");
                isEnteringTask = true;
            } else if (txt.equals("/stop")) {
                stopTaskSchedulerForUser(id);
                sendText(id, "Your alarm has been stopped.");
            }
        } else if (isEnteringTask) {
            String[] parts = txt.split(" - ", 2);
            if (parts.length == 2) {
                String description = parts[1].trim();
                String timeString = parts[0].trim();

                DateTimeFormatter formatter = DateTimeFormatter.ofPattern("HH:mm");

                try {
                    LocalTime scheduledTime = LocalTime.parse(timeString, formatter);

                    Task task = new Task();
                    task.setUserId(id);
                    task.setDescription(description);
                    task.setScheduledTime(scheduledTime);
                    task.setAlarmActive(false);

                    taskRepository.save(task);

                    sendText(id, "Task saved successfully!");

                    isEnteringTask = false;
                } catch (DateTimeParseException e) {
                    sendText(id, "Invalid time format. Please use HH:mm - Task description.");
                }
            } else {
                sendText(id, "Invalid format. Please use HH:mm - Task description.");
            }
        }

    }

    @Transactional
    @Scheduled(fixedRate = 10000)
    public void taskRunner() {
        List<Task> tasks = taskRepository.findAll();


        for (Task task : tasks) {
            LocalTime scheduledTime = task.getScheduledTime();
            LocalTime currentTime = LocalTime.now();


            if (scheduledTime.getHour() == currentTime.getHour() && scheduledTime.getMinute() == currentTime.getMinute()) {
                taskRepository.setAlarmActive(task.getId());
            }

            scheduleTaskForUser(task);

        }
    }







    private void sendText(Long chatId, String text) {
        SendMessage sm = new SendMessage();
        sm.setChatId(chatId.toString());
        sm.setText(text);

        try {
            execute(sm);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }

    private void scheduleTaskForUser(Task task) {
        LocalTime currentTime = LocalTime.now();
        LocalTime scheduledTime = task.getScheduledTime();

        if (task.getAlarmActive() && isScheduledTime(currentTime, scheduledTime)) {
            ScheduledExecutorService executor = Executors.newScheduledThreadPool(1);

            // Calculate the initial delay to reach the next 5-minute interval
            long initialDelayMillis = calculateInitialDelay(currentTime, scheduledTime);

            // Schedule the task to run every 5 minutes
            ScheduledFuture<?> scheduledFuture = executor.scheduleAtFixedRate(() -> {
                sendText(task.getUserId(), "Scheduled task: " + task.getDescription());
            }, initialDelayMillis, 1 * 60 * 1000, TimeUnit.MILLISECONDS);

            // Store the ScheduledFuture for potential future cancellation
            userSchedulers.put(task.getUserId(), scheduledFuture);
        }
    }

    private boolean isScheduledTime(LocalTime currentTime, LocalTime scheduledTime) {
        return currentTime.getHour() == scheduledTime.getHour() && currentTime.getMinute() == scheduledTime.getMinute();
    }

    private long calculateInitialDelay(LocalTime currentTime, LocalTime scheduledTime) {
        Duration duration = Duration.between(currentTime, scheduledTime);

        // Calculate the next 5-minute interval
        long nextInterval = (duration.getSeconds() / 300) * 300;

        return Math.max(nextInterval * 1000, 0);
    }

    @Transactional
    public void stopTaskSchedulerForUser(Long userId) {

        taskRepository.deleteByUserId(userId);

        // Cancel the ScheduledFuture
        ScheduledFuture<?> future = userSchedulers.get(userId);
        if (future != null) {
            future.cancel(true);
            userSchedulers.remove(userId);
        }
    }


}
