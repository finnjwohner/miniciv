import type { GameView } from './api'

function GameScreen({ game }: { game: GameView }) {
  return (
    <section>
      <h2>Turn {game.turn}</h2>
      <h3>Nations</h3>
      <ul>
        {game.nations.map((nation) => (
          <li key={nation}>{nation}</li>
        ))}
      </ul>
    </section>
  )
}

export default GameScreen
