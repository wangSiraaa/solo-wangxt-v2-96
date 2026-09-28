import type { ReplayStep } from '../replay';

interface Props {
  steps: ReplayStep[];
  current: number; // 已执行到的步骤数（0 表示未开始）
  playing: boolean;
  onPlay: () => void;
  onStep: () => void;
  onReset: () => void;
}

/**
 * 固定命令回放面板：逐步录制页面变化。
 * 每一步都给出命令、页面讲解与预期结果，便于课堂对照核对。
 */
export default function ReplayPanel({ steps, current, playing, onPlay, onStep, onReset }: Props) {
  return (
    <section className="card replay">
      <h2>固定命令回放脚本（可录制）</h2>
      <p className="hint">
        顺序：同价排队 → 吃掉两档报价 → 部分成交剩余挂簿 → 撤销剩余 → 重复 clientOrderId 幂等拦截。
        每步执行后右侧各面板与守恒核对条同步变化。
      </p>
      <div className="replay-controls">
        <button className="primary" onClick={onPlay} disabled={playing || current >= steps.length}>
          {playing ? '回放中…' : current === 0 ? '▶ 从头自动回放' : '▶ 继续自动回放'}
        </button>
        <button onClick={onStep} disabled={playing || current >= steps.length}>
          ⏭ 单步执行（{current}/{steps.length}）
        </button>
        <button onClick={onReset} disabled={playing}>⟲ 复位脚本</button>
      </div>
      <ol className="script">
        {steps.map((step, i) => {
          const done = i < current;
          const active = i === current;
          return (
            <li key={i} className={done ? 'done' : active ? 'active' : 'todo'}>
              <div className="step-head">
                <span className="step-no">{done ? '✓' : i + 1}</span>
                <code className="command">{step.command}</code>
              </div>
              <div className="narration">{step.narration}</div>
              <div className="expect">预期：{step.expect}</div>
            </li>
          );
        })}
      </ol>
    </section>
  );
}
