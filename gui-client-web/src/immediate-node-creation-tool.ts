import { NodeCreationTool, CreateNodeOperation, EnableDefaultToolsAction } from '@eclipse-glsp/client';
import { injectable } from 'inversify';

@injectable()
export class ImmediateNodeCreationTool extends NodeCreationTool {
    override doEnable(): void {
        this.actionDispatcher.dispatchAll([
            CreateNodeOperation.create(this.triggerAction.elementTypeId, { args: this.triggerAction.args }),
            EnableDefaultToolsAction.create()
        ]);
    }
}