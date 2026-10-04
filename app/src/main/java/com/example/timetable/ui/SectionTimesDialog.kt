package com.example.timetable.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.timetable.data.SectionTime

/**
 * 作息时间设置对话框。
 *
 * 逐节编辑每节课的开始 / 结束时间（格式 HH:mm），
 * 也可一键恢复默认作息。
 */
@Composable
fun SectionTimesDialog(
    sectionTimes: List<SectionTime>,
    onDismiss: () -> Unit,
    onSave: (List<SectionTime>) -> Unit,
) {
    // 用可变列表保存编辑中的值，允许用户临时输入非法内容
    var draft by remember {
        mutableStateOf(
            sectionTimes.map { it.start to it.end },
        )
    }

    val allValid = draft.all { (s, e) -> isTimeLike(s) && isTimeLike(e) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("作息时间") },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 440.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = "时间格式 HH:mm，例如 08:00。修改后会立即应用到课表左侧的节次栏。",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                draft.forEachIndexed { index, (start, end) ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = "第 ${index + 1} 节",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.width(56.dp),
                        )
                        TimeField(
                            value = start,
                            label = "开始",
                            isError = start.isNotEmpty() && !isTimeLike(start),
                            modifier = Modifier.weight(1f),
                        ) { v ->
                            draft = draft.toMutableList().also { it[index] = v to it[index].second }
                        }
                        TimeField(
                            value = end,
                            label = "结束",
                            isError = end.isNotEmpty() && !isTimeLike(end),
                            modifier = Modifier.weight(1f),
                        ) { v ->
                            draft = draft.toMutableList().also { it[index] = it[index].first to v }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSave(draft.map { SectionTime(it.first.trim(), it.second.trim()) })
                    onDismiss()
                },
                enabled = allValid && draft.isNotEmpty(),
            ) { Text("保存") }
        },
        dismissButton = {
            Row {
                TextButton(
                    onClick = { draft = SectionTime.defaults().map { it.start to it.end } },
                ) { Text("恢复默认") }
                TextButton(onClick = onDismiss) { Text("取消") }
            }
        },
    )
}

@Composable
private fun TimeField(
    value: String,
    label: String,
    isError: Boolean,
    modifier: Modifier = Modifier,
    onValueChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = { input ->
            // 只允许数字与冒号，最长 5 位（HH:mm）
            onValueChange(input.filter { it.isDigit() || it == ':' }.take(5))
        },
        label = { Text(label, fontSize = 11.sp) },
        singleLine = true,
        isError = isError,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = modifier,
    )
}

/** 宽松校验：形如 8:00 / 08:00 / 08:0，允许用户输入过程中不完整 */
private fun isTimeLike(text: String): Boolean {
    val t = text.trim()
    if (t.isEmpty()) return false
    val parts = t.split(":")
    if (parts.size != 2) return false
    val h = parts[0].toIntOrNull() ?: return false
    val m = parts[1].toIntOrNull() ?: return false
    return h in 0..23 && m in 0..59
}
