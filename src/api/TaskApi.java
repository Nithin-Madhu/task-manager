package api;

import exception.ApiException;
import model.Task;
import model.TaskQuery;
import model.TaskRequest;
import service.TaskService;

public class TaskApi {
    private final TaskService service;

    public TaskApi(TaskService service) {
        this.service = service;
    }

    public ApiResponse createTask(TaskRequest request, String idempotencyKey) {
        String fingerprint = fingerprint("createTask", request);
        return IdempotencyStore.getInstance().execute(idempotencyKey, fingerprint,
                () -> doCreateTask(request));
    }

    private ApiResponse doCreateTask(TaskRequest request) {
        try {
            return new ApiResponse(201, JsonUtil.toJson(service.createTask(request)));
        } catch (ApiException e) {
            return ErrorResponseFactory.from(e);
        } catch (Exception e) {
            return ErrorResponseFactory.internalError();
        }
    }

    private static String fingerprint(String operation, TaskRequest r) {
        if (r == null) return operation + "\u001F<null request>";
        return String.join("\u001F", operation,
                nz(r.getTitle()), nz(r.getDescription()), nz(r.getCompleted()),
                nz(r.getDueDate()), nz(r.getPriority()));
    }

    private static String nz(String s) {
        return s == null ? "\u0000" : s;
    }

    public ApiResponse getTask(int id) {
        try {
            return new ApiResponse(200, JsonUtil.toJson(service.getTask(id)));
        } catch (ApiException e) {
            return ErrorResponseFactory.from(e);
        } catch (Exception e) {
            return ErrorResponseFactory.internalError();
        }
    }

    public ApiResponse listTasks(TaskQuery query) {
        try {
            return new ApiResponse(200, JsonUtil.toJson(service.listTasks(query)));
        } catch (ApiException e) {
            return ErrorResponseFactory.from(e);
        } catch (Exception e) {
            return ErrorResponseFactory.internalError();
        }
    }

    public ApiResponse listByPriority(String level) {
        try {
            return new ApiResponse(200, JsonUtil.toJson(service.listByPriority(level)));
        } catch (ApiException e) {
            return ErrorResponseFactory.from(e);
        } catch (Exception e) {
            return ErrorResponseFactory.internalError();
        }
    }

    public ApiResponse updateTask(int id, TaskRequest request) {
        try {
            return new ApiResponse(200, JsonUtil.toJson(service.updateTask(id, request)));
        } catch (ApiException e) {
            return ErrorResponseFactory.from(e);
        } catch (Exception e) {
            return ErrorResponseFactory.internalError();
        }
    }

    public ApiResponse deleteTask(int id) {
        try {
            service.deleteTask(id);
            return new ApiResponse(200, "{\"deleted\":true,\"id\":" + id + "}");
        } catch (ApiException e) {
            return ErrorResponseFactory.from(e);
        } catch (Exception e) {
            return ErrorResponseFactory.internalError();
        }
    }

    public ApiResponse createSubtask(int parentId, TaskRequest request) {
        try {
            return new ApiResponse(201, JsonUtil.toJson(service.createSubtask(parentId, request)));
        } catch (ApiException e) {
            return ErrorResponseFactory.from(e);
        } catch (Exception e) {
            return ErrorResponseFactory.internalError();
        }
    }

    public ApiResponse listSubtasks(int parentId) {
        try {
            return new ApiResponse(200, JsonUtil.toJson(service.listSubtasks(parentId)));
        } catch (ApiException e) {
            return ErrorResponseFactory.from(e);
        } catch (Exception e) {
            return ErrorResponseFactory.internalError();
        }
    }
}